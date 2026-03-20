package cc.getportal;

import org.jetbrains.annotations.Nullable;

/**
 * Configuration for {@link PortalClient}.
 *
 * <pre>{@code
 * // Manual polling (default — no background threads)
 * PortalClientConfig.create("http://localhost:3000", "token")
 *
 * // Auto-polling (background scheduler)
 * PortalClientConfig.create("http://localhost:3000", "token").autoPolling(500)
 *
 * // Webhooks
 * PortalClientConfig.create("http://localhost:3000", "token").webhookSecret("secret")
 * }</pre>
 */
public class PortalClientConfig {

    public final String baseUrl;
    public final String authToken;

    long autoPollingIntervalMs = 0; // 0 = disabled
    @Nullable String webhookSecret = null;

    private PortalClientConfig(String baseUrl, String authToken) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
    }

    /** Create a base config. No background threads are started by default. */
    public static PortalClientConfig create(String baseUrl, String authToken) {
        return new PortalClientConfig(baseUrl, authToken);
    }

    /**
     * Enable automatic background polling. The client will start a scheduler
     * that polls all active streams every {@code intervalMs} milliseconds,
     * resolving their {@code done} futures automatically.
     */
    public PortalClientConfig autoPolling(long intervalMs) {
        this.autoPollingIntervalMs = intervalMs;
        return this;
    }

    /**
     * Enable webhook signature verification. When set, {@code deliverWebhookPayload(rawBody, signature)}
     * will verify the {@code X-Portal-Signature} HMAC-SHA256 header before delivering.
     */
    public PortalClientConfig webhookSecret(String secret) {
        this.webhookSecret = secret;
        return this;
    }

    public boolean isAutoPollingEnabled() {
        return autoPollingIntervalMs > 0;
    }
}
