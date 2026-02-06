package cc.getportal;

import cc.getportal.command.PortalNotification;
import cc.getportal.command.PortalRequest;
import cc.getportal.command.PortalResponse;
import cc.getportal.command.request.AuthRequest;
import cc.getportal.model.Currency;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PortalSDK {

    private static final Logger logger = LoggerFactory.getLogger(PortalSDK.class);

    private final ConcurrentHashMap<String, RegisteredCommand<?, ?>> commands = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RegisteredNotification<?>> activeStreams = new ConcurrentHashMap<>();

    private final Gson gson;
    private final String wsEndpoint;
    Runnable onClose;

    String authToken = "";
    private boolean connected = false;
    private PortalWsClient wsClient;

    public PortalSDK(@NotNull String wsEndpoint) {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Response.class, new ResponseDeserializer())
                .registerTypeAdapter(Currency.class, new CurrencySerializer())
                .registerTypeAdapter(Currency.class, new CurrencyDeserializer())
                .create();
        this.wsEndpoint = wsEndpoint;
    }

    public PortalSDK onClose(@NotNull Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    public void connect() throws InterruptedException {
        wsClient = new PortalWsClient(URI.create(this.wsEndpoint), this);
        wsClient.connectBlocking();
    }

    public void authenticate(@NotNull String authToken) {
        this.authToken = authToken;
        internalSendCommand(new AuthRequest(this.authToken), (res, err) -> {
            if(err != null) {
                logger.error("error auth request: {}", err);
                return;
            }   
            logger.info("Authenticated: {}", res.message());
        });
    }

    public void disconnect() throws InterruptedException {
        connected = false;

        wsClient.close();

        authToken = "";
        commands.clear();
        activeStreams.clear();
    }

    public <T extends PortalRequest<E, N>, E extends PortalResponse, N extends PortalNotification> void sendCommand(@NotNull T req, @NotNull BiConsumer<E, String> fun) {
        if (!connected) {
            throw new PortalSDKException("not connected. Use PortalSDK#connect() before.");
        }
        internalSendCommand(req,fun);
    }

    <T extends PortalRequest<E, N>, E extends PortalResponse, N extends PortalNotification> void internalSendCommand(@NotNull T req, @NotNull BiConsumer<E, String> fun) {
        var id = generateId();
        var p = req.isUnit() ? null : this.gson.toJson(req);
        var command = String.format("""
                {
                "id": "%s",
                "cmd": "%s",
                "params": %s
                }
                
                """, id, req.name(), p);
        logger.debug("Sending command {}: {}", req.name(), command);

        RegisteredNotification<N> registeredNotification = null;
        if (req.notificationType() != null) {
            registeredNotification = new RegisteredNotification<>(req.notificationType(), req.notificationFun());
        }
        this.commands.put(id, new RegisteredCommand<>(req.name(), req.responseType(), fun, registeredNotification));
        wsClient.send(command);
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    // Internal methods

    void setConnected(boolean connected) {
        this.connected = connected;
    }

    void callFun(@NotNull String msg) {
        Response message = gson.fromJson(msg, Response.class);
        if (message.isSuccess()) {
            Response.Success success = message.success();
            RegisteredCommand registeredCommand = this.commands.get(success.id);
            if (registeredCommand == null) {
                logger.warn("Ignoring success for unknown command id: {}", success.id);
                return;
            }

            PortalResponse portalResponse = (PortalResponse) gson.fromJson(success.jsonElement, registeredCommand.responseType);
            registeredCommand.fun.accept(portalResponse, null);

            // check if stream_id is present

            String streamId = success.streamId;
            if(streamId != null) {
                logger.debug("stream id present {}", streamId);

                var registeredNotification = registeredCommand.registeredNotification;
                if(registeredNotification == null) {
                    logger.error("Losing stream id {} because no registered notification", streamId);
                } else {
                    this.activeStreams.put(streamId, registeredNotification);
                }
            }
        }

        if(message.isNotification()) {
            Response.Notification notification = message.notification();

            RegisteredNotification registeredNotification = this.activeStreams.get(notification.id);
            if (registeredNotification == null) {
                logger.warn("Ignoring notification for unknown stream id: {}", notification.id);
                return;
            }

            PortalNotification portalNotification = (PortalNotification) gson.fromJson(notification.jsonElement, registeredNotification.notificationType);
            registeredNotification.fun.accept(portalNotification);


            // remove from active streams map
            if(portalNotification.deleteStream()) {
                this.activeStreams.remove(notification.id);
            }

        }

        if(message.isError()) {
            Response.Error error = message.error();

            RegisteredCommand registeredCommand = this.commands.get(error.id);
            if (registeredCommand == null) {
                logger.warn("Ignoring error for unknown command id: {}: {}", error.id, error.message);
                return;
            }
            registeredCommand.fun.accept(null, error.message);
        }
    }

    record RegisteredCommand<E extends PortalResponse, N extends PortalNotification>(String cmd, Class<E> responseType, BiConsumer<E, String> fun,  @Nullable RegisteredNotification<N> registeredNotification) {}

    record RegisteredNotification<N extends PortalNotification>(Class<N> notificationType, Consumer<N> fun){}
}
