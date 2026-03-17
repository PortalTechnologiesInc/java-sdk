package cc.getportal;

import cc.getportal.model.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Portal REST API client (Java 17+).
 *
 * <p>Configure via {@link PortalClientConfig}:
 * <ul>
 *   <li><b>Manual polling</b> — call {@link #pollUntilComplete} yourself</li>
 *   <li><b>Auto-polling</b> — background scheduler resolves {@code done()} automatically</li>
 *   <li><b>Webhooks</b> — call {@link #deliverWebhookPayload} from your HTTP server</li>
 * </ul>
 *
 * <p>All three modes use the same {@code AsyncOperation<T>} return type —
 * only how terminal events are delivered differs.
 */
public class PortalClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PortalClient.class);

    private final PortalClientConfig config;
    private final HttpClient http;
    private final Gson gson;

    /** Pending streams: future resolves with terminal raw JSON, mapped to T by the registered mapper. */
    private final ConcurrentHashMap<String, PendingStream> pending = new ConcurrentHashMap<>();

    @Nullable private ScheduledExecutorService pollingScheduler;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public PortalClient(PortalClientConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Currency.class, new CurrencySerializer())
                .registerTypeAdapter(Currency.class, new CurrencyDeserializer())
                .registerTypeAdapterFactory(new RawJsonTypeAdapterFactory())
                .create();

        if (config.isAutoPollingEnabled()) {
            startPollingScheduler(config.autoPollingIntervalMs);
        }
    }

    /** Shut down the auto-polling scheduler (if started). */
    @Override
    public void close() {
        if (pollingScheduler != null) {
            pollingScheduler.shutdown();
        }
    }

    // -------------------------------------------------------------------------
    // Auto-polling scheduler
    // -------------------------------------------------------------------------

    private void startPollingScheduler(long intervalMs) {
        pollingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "portal-poller");
            t.setDaemon(true);
            return t;
        });
        pollingScheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, PendingStream> entry : pending.entrySet()) {
                String streamId = entry.getKey();
                PendingStream ps = entry.getValue();
                try {
                    int last = ps.lastIndex().get();
                    EventsResponse resp = getEvents(streamId, last < 0 ? null : last);
                    if (resp == null || resp.events == null) continue;
                    for (StreamEvent event : resp.events) {
                        ps.lastIndex().set(event.index);
                        notifyListeners(ps, event);
                        if (event.isTerminal()) {
                            pending.remove(streamId);
                            ps.future().complete(event._raw != null ? event._raw : new JsonObject());
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Auto-poll error for stream {}: {}", streamId, e.getMessage());
                }
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
        log.debug("Auto-polling started (interval={}ms)", intervalMs);
    }

    // -------------------------------------------------------------------------
    // Internal HTTP helpers
    // -------------------------------------------------------------------------

    private static class ApiResponse {
        boolean success;
        JsonElement data;
        String error;
    }

    private <T> T request(String method, String path, @Nullable Object body, Type responseType)
            throws IOException, InterruptedException, PortalSDKException {
        String url = config.baseUrl.replaceAll("/+$", "") + path;
        log.debug("{} {}", method, url);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30));

        if (config.authToken != null) {
            builder.header("Authorization", "Bearer " + config.authToken);
        }

        String jsonBody = (body != null) ? gson.toJson(body) : "{}";
        switch (method) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody));
            default -> {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            }
        }

        HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String ct = resp.headers().firstValue("content-type").orElse("");

        if (!ct.contains("application/json")) {
            if (resp.statusCode() >= 400) throw new PortalSDKException("HTTP " + resp.statusCode() + ": " + resp.body());
            if (responseType == String.class) //noinspection unchecked
                return (T) resp.body();
            return null;
        }

        ApiResponse wrapper = gson.fromJson(resp.body(), ApiResponse.class);
        if (!wrapper.success) {
            throw new PortalSDKException(wrapper.error != null ? wrapper.error : "API error (HTTP " + resp.statusCode() + ")");
        }
        if (wrapper.data == null || responseType == Void.class) return null;
        //noinspection unchecked
        return (T) gson.fromJson(wrapper.data, responseType);
    }

    private <T> T get(String path, Type type) throws IOException, InterruptedException, PortalSDKException {
        return request("GET", path, null, type);
    }

    private <T> T post(String path, @Nullable Object body, Type type) throws IOException, InterruptedException, PortalSDKException {
        return request("POST", path, body, type);
    }

    private <T> T delete(String path, Object body, Type type) throws IOException, InterruptedException, PortalSDKException {
        return request("DELETE", path, body, type);
    }

    // -------------------------------------------------------------------------
    // Pending stream management
    // -------------------------------------------------------------------------

    private record PendingStream(
            CompletableFuture<JsonObject> future,
            CopyOnWriteArrayList<Consumer<StreamEvent>> listeners,
            AtomicInteger lastIndex
    ) {}

    private <T> AsyncOperation<T> registerStream(String streamId, Function<JsonObject, T> mapper) {
        CompletableFuture<JsonObject> raw = new CompletableFuture<>();
        pending.put(streamId, new PendingStream(raw, new CopyOnWriteArrayList<>(), new AtomicInteger(-1)));
        return new AsyncOperation<>(streamId, raw.thenApply(mapper));
    }

    private void notifyListeners(PendingStream ps, StreamEvent event) {
        for (Consumer<StreamEvent> l : ps.listeners()) {
            try { l.accept(event); } catch (Exception e) { log.warn("Listener error", e); }
        }
    }

    private void deliverEvent(String streamId, StreamEvent event) {
        PendingStream ps = pending.get(streamId);
        if (ps == null) {
            log.debug("No pending stream for {}, ignoring event type={}", streamId, event.type);
            return;
        }
        notifyListeners(ps, event);
        if (event.isTerminal()) {
            pending.remove(streamId);
            ps.future().complete(event._raw != null ? event._raw : new JsonObject());
        }
    }

    /**
     * Subscribe to intermediate events for a stream (non-terminal included).
     *
     * @return unsubscribe handle
     */
    public Runnable onEvent(String streamId, Consumer<StreamEvent> callback) {
        PendingStream ps = pending.get(streamId);
        if (ps == null) return () -> {};
        ps.listeners().add(callback);
        return () -> ps.listeners().remove(callback);
    }

    /** Cancel a pending stream. */
    public void cancel(String streamId) {
        PendingStream ps = pending.remove(streamId);
        if (ps != null) ps.future().completeExceptionally(new PortalSDKException("Stream cancelled: " + streamId));
    }

    // -------------------------------------------------------------------------
    // Health / Version / Info
    // -------------------------------------------------------------------------

    public String health() throws IOException, InterruptedException, PortalSDKException {
        return get("/health", String.class);
    }

    public VersionResponse version() throws IOException, InterruptedException, PortalSDKException {
        return get("/version", VersionResponse.class);
    }

    public InfoResponse info() throws IOException, InterruptedException, PortalSDKException {
        return get("/info", InfoResponse.class);
    }

    // -------------------------------------------------------------------------
    // Key Handshake
    // -------------------------------------------------------------------------

    public AsyncOperation<KeyHandshakeResult> newKeyHandshakeUrl(
            @Nullable String staticToken, boolean noRequest
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("static_token", staticToken);
        body.put("no_request", noRequest);
        JsonObject resp = post("/key-handshake", body, JsonObject.class);
        String streamId = resp.get("stream_id").getAsString();
        return registerStream(streamId, json -> gson.fromJson(json, KeyHandshakeResult.class));
    }

    // -------------------------------------------------------------------------
    // Authenticate Key
    // -------------------------------------------------------------------------

    public AsyncOperation<AuthResponseData> authenticateKey(
            String mainKey, List<String> subkeys
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = Map.of("main_key", mainKey, "subkeys", subkeys);
        JsonObject resp = post("/authenticate-key", body, JsonObject.class);
        String streamId = resp.get("stream_id").getAsString();
        return registerStream(streamId, json -> gson.fromJson(json, AuthResponseData.class));
    }

    // -------------------------------------------------------------------------
    // Payments
    // -------------------------------------------------------------------------

    public AsyncOperation<InvoiceStatus> requestSinglePayment(
            String mainKey, List<String> subkeys, SinglePaymentRequestContent content
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("main_key", mainKey);
        body.put("subkeys", subkeys);
        body.putAll(toMap(content));
        JsonObject resp = post("/payments/single", body, JsonObject.class);
        String streamId = resp.get("stream_id").getAsString();
        return registerStream(streamId,
                json -> gson.fromJson(json.getAsJsonObject("status"), InvoiceStatus.class));
    }

    public AsyncOperation<InvoiceStatus> requestPaymentRaw(
            String mainKey, List<String> subkeys, InvoiceRequestContent content
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("main_key", mainKey);
        body.put("subkeys", subkeys);
        body.putAll(toMap(content));
        JsonObject resp = post("/payments/raw", body, JsonObject.class);
        String streamId = resp.get("stream_id").getAsString();
        return registerStream(streamId,
                json -> gson.fromJson(json.getAsJsonObject("status"), InvoiceStatus.class));
    }

    public AsyncOperation<RecurringPaymentResponseContent> requestRecurringPayment(
            String mainKey, List<String> subkeys, RecurringPaymentRequestContent content
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("main_key", mainKey);
        body.put("subkeys", subkeys);
        body.putAll(toMap(content));
        JsonObject resp = post("/payments/recurring", body, JsonObject.class);
        String streamId = resp.get("stream_id").getAsString();
        return registerStream(streamId,
                json -> gson.fromJson(json.getAsJsonObject("status"), RecurringPaymentResponseContent.class));
    }

    public String closeRecurringPayment(
            String mainKey, List<String> subkeys, String subscriptionId
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = Map.of(
                "main_key", mainKey, "subkeys", subkeys, "subscription_id", subscriptionId);
        JsonObject resp = post("/payments/recurring/close", body, JsonObject.class);
        return resp.get("message").getAsString();
    }

    // -------------------------------------------------------------------------
    // Profile
    // -------------------------------------------------------------------------

    public @Nullable Profile fetchProfile(String mainKey)
            throws IOException, InterruptedException, PortalSDKException {
        String path = "/profile/" + java.net.URLEncoder.encode(mainKey, StandardCharsets.UTF_8);
        JsonObject resp = get(path, JsonObject.class);
        JsonElement profileEl = resp.get("profile");
        if (profileEl == null || profileEl.isJsonNull()) return null;
        return gson.fromJson(profileEl, Profile.class);
    }

    // -------------------------------------------------------------------------
    // Invoices
    // -------------------------------------------------------------------------

    public AsyncOperation<InvoicePaymentResponse> requestInvoice(
            String recipientKey, List<String> subkeys, RequestInvoiceParams params
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("recipient_key", recipientKey);
        body.put("subkeys", subkeys);
        body.putAll(toMap(params));
        JsonObject resp = post("/invoices/request", body, JsonObject.class);
        String streamId = resp.get("stream_id").getAsString();
        return registerStream(streamId, json -> gson.fromJson(json, InvoicePaymentResponse.class));
    }

    public PayInvoiceResponse payInvoice(String invoice)
            throws IOException, InterruptedException, PortalSDKException {
        return post("/invoices/pay", Map.of("invoice", invoice), PayInvoiceResponse.class);
    }

    // -------------------------------------------------------------------------
    // JWT
    // -------------------------------------------------------------------------

    public String issueJwt(String targetKey, int durationHours)
            throws IOException, InterruptedException, PortalSDKException {
        JsonObject resp = post("/jwt/issue",
                Map.of("target_key", targetKey, "duration_hours", durationHours), JsonObject.class);
        return resp.get("token").getAsString();
    }

    public String verifyJwt(String pubkey, String token)
            throws IOException, InterruptedException, PortalSDKException {
        JsonObject resp = post("/jwt/verify", Map.of("pubkey", pubkey, "token", token), JsonObject.class);
        return resp.get("target_key").getAsString();
    }

    // -------------------------------------------------------------------------
    // Cashu
    // -------------------------------------------------------------------------

    public AsyncOperation<CashuResponseStatus> requestCashu(
            String recipientKey, List<String> subkeys,
            String mintUrl, String unit, long amount
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = Map.of(
                "recipient_key", recipientKey, "subkeys", subkeys,
                "mint_url", mintUrl, "unit", unit, "amount", amount);
        JsonObject resp = post("/cashu/request", body, JsonObject.class);
        String streamId = resp.get("stream_id").getAsString();
        return registerStream(streamId,
                json -> gson.fromJson(json.getAsJsonObject("status"), CashuResponseStatus.class));
    }

    public String sendCashuDirect(String mainKey, List<String> subkeys, String token)
            throws IOException, InterruptedException, PortalSDKException {
        JsonObject resp = post("/cashu/send-direct",
                Map.of("main_key", mainKey, "subkeys", subkeys, "token", token), JsonObject.class);
        return resp.get("message").getAsString();
    }

    public String mintCashu(
            String mintUrl, String unit, long amount,
            @Nullable String staticAuthToken, @Nullable String description
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("mint_url", mintUrl);
        body.put("unit", unit);
        body.put("amount", amount);
        if (staticAuthToken != null) body.put("static_auth_token", staticAuthToken);
        if (description != null) body.put("description", description);
        JsonObject resp = post("/cashu/mint", body, JsonObject.class);
        return resp.get("token").getAsString();
    }

    public long burnCashu(
            String mintUrl, String unit, String token, @Nullable String staticAuthToken
    ) throws IOException, InterruptedException, PortalSDKException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("mint_url", mintUrl);
        body.put("unit", unit);
        body.put("token", token);
        if (staticAuthToken != null) body.put("static_auth_token", staticAuthToken);
        JsonObject resp = post("/cashu/burn", body, JsonObject.class);
        return resp.get("amount").getAsLong();
    }

    // -------------------------------------------------------------------------
    // Relays
    // -------------------------------------------------------------------------

    public String addRelay(String relay) throws IOException, InterruptedException, PortalSDKException {
        JsonObject resp = post("/relays", Map.of("relay", relay), JsonObject.class);
        return resp.get("relay").getAsString();
    }

    public String removeRelay(String relay) throws IOException, InterruptedException, PortalSDKException {
        JsonObject resp = delete("/relays", Map.of("relay", relay), JsonObject.class);
        return resp.get("relay").getAsString();
    }

    // -------------------------------------------------------------------------
    // Calendar
    // -------------------------------------------------------------------------

    public @Nullable Long calculateNextOccurrence(String calendar, long from)
            throws IOException, InterruptedException, PortalSDKException {
        JsonObject resp = post("/calendar/next-occurrence",
                Map.of("calendar", calendar, "from", from), JsonObject.class);
        JsonElement next = resp.get("next_occurrence");
        if (next == null || next.isJsonNull()) return null;
        return next.getAsLong();
    }

    // -------------------------------------------------------------------------
    // NIP-05
    // -------------------------------------------------------------------------

    public Nip05Profile fetchNip05Profile(String nip05)
            throws IOException, InterruptedException, PortalSDKException {
        String path = "/nip05/" + java.net.URLEncoder.encode(nip05, StandardCharsets.UTF_8);
        JsonObject resp = get(path, JsonObject.class);
        return gson.fromJson(resp.get("profile"), Nip05Profile.class);
    }

    // -------------------------------------------------------------------------
    // Wallet
    // -------------------------------------------------------------------------

    public WalletInfoResponse getWalletInfo() throws IOException, InterruptedException, PortalSDKException {
        return get("/wallet/info", WalletInfoResponse.class);
    }

    // -------------------------------------------------------------------------
    // Events (low-level)
    // -------------------------------------------------------------------------

    public EventsResponse getEvents(String streamId, @Nullable Integer after)
            throws IOException, InterruptedException, PortalSDKException {
        String path = "/events/" + java.net.URLEncoder.encode(streamId, StandardCharsets.UTF_8)
                + (after != null ? "?after=" + after : "");
        return get(path, EventsResponse.class);
    }

    // -------------------------------------------------------------------------
    // Manual polling
    // -------------------------------------------------------------------------

    /**
     * Manually poll until the operation resolves. Blocks the calling thread.
     * <p>
     * Use this when auto-polling is not enabled and you want synchronous-style code.
     * If auto-polling is active, you don't need to call this — use {@code op.done()} instead.
     *
     * @param operation the async operation to wait for
     * @param options   polling interval, timeout, and per-event callback
     */
    public <T> T pollUntilComplete(AsyncOperation<T> operation, PollOptions options)
            throws PortalSDKException, IOException, InterruptedException {
        String streamId = operation.streamId();
        long startMs = System.currentTimeMillis();
        Integer lastIndex = null;

        while (true) {
            if (options.timeoutMs > 0 && System.currentTimeMillis() - startMs > options.timeoutMs) {
                throw new PortalSDKException("Polling timed out for stream: " + streamId);
            }

            EventsResponse response = getEvents(streamId, lastIndex);
            if (response != null && response.events != null) {
                for (StreamEvent event : response.events) {
                    lastIndex = event.index;
                    if (options.onEvent != null) options.onEvent.accept(event);
                    deliverEvent(streamId, event);
                    if (event.isTerminal()) {
                        try {
                            return operation.done().get();
                        } catch (ExecutionException | InterruptedException e) {
                            throw new PortalSDKException("Stream failed: " + e.getCause().getMessage());
                        }
                    }
                }
            }
            Thread.sleep(options.intervalMs);
        }
    }

    // -------------------------------------------------------------------------
    // Webhook delivery
    // -------------------------------------------------------------------------

    /**
     * Verify the {@code X-Portal-Signature} header and deliver the webhook payload.
     * Requires {@link PortalClientConfig#webhookSecret(String)} to be set.
     *
     * @param rawBody   raw request body bytes — do NOT parse before calling this
     * @param signature value of the {@code X-Portal-Signature} header
     */
    public void deliverWebhookPayload(byte[] rawBody, String signature) throws PortalSDKException {
        if (config.webhookSecret == null) throw new PortalSDKException("No webhookSecret configured");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody);
            String hex = HexFormat.of().formatHex(computed);
            if (!MessageDigest.isEqual(
                    hex.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new PortalSDKException("Invalid webhook signature");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new PortalSDKException("HMAC error: " + e.getMessage());
        }
        WebhookPayload payload = gson.fromJson(new String(rawBody, StandardCharsets.UTF_8), WebhookPayload.class);
        deliverWebhookPayload(payload);
    }

    /** Deliver an already-parsed webhook payload (skips signature verification). */
    public void deliverWebhookPayload(WebhookPayload payload) {
        log.debug("webhook received streamId={} type={}", payload.streamId, payload.type);
        StreamEvent thin = new StreamEvent();
        thin.type = payload.type;
        thin.index = payload.index;
        thin.timestamp = payload.timestamp;
        thin._raw = payload._raw;
        deliverEvent(payload.streamId, thin);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        JsonObject json = gson.toJsonTree(obj).getAsJsonObject();
        return (Map<String, Object>) gson.fromJson(json, Map.class);
    }

    // -------------------------------------------------------------------------
    // TypeAdapterFactory — populates _raw on StreamEvent and WebhookPayload
    // -------------------------------------------------------------------------

    private static class RawJsonTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> raw = type.getRawType();
            boolean isStreamEvent = StreamEvent.class.isAssignableFrom(raw);
            boolean isWebhookPayload = WebhookPayload.class == raw;
            if (!isStreamEvent && !isWebhookPayload) return null;

            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            TypeAdapter<JsonObject> jsonObjAdapter = gson.getAdapter(JsonObject.class);

            return new TypeAdapter<>() {
                @Override
                public void write(com.google.gson.stream.JsonWriter out, T value) throws java.io.IOException {
                    delegate.write(out, value);
                }

                @Override
                public T read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                    JsonObject jo = jsonObjAdapter.read(in);
                    T result = delegate.fromJsonTree(jo);
                    if (result instanceof StreamEvent se) se._raw = jo;
                    else if (result instanceof WebhookPayload wp) wp._raw = jo;
                    return result;
                }
            };
        }
    }
}
