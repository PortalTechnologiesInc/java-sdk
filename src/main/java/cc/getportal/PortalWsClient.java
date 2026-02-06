package cc.getportal;

import java.net.URI;
import java.nio.ByteBuffer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PortalWsClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(PortalWsClient.class);

    private final PortalSDK client;

    public PortalWsClient(URI serverUri, Draft draft, PortalSDK client) {
        super(serverUri, draft);
        this.client = client;
    }

    public PortalWsClient(URI serverURI, PortalSDK client) {
        super(serverURI);
        this.client = client;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.debug("new connection opened");
        client.setConnected(true);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("closed with exit code '{}' additional info: '{}'", code, reason);

        client.setConnected(false);

        // Run function on close (catch so callback cannot kill WebSocket thread)
        if (client.onClose != null) {
            try {
                client.onClose.run();
            } catch (Exception e) {
                logger.error("onClose callback threw", e);
            }
        }
    }

    @Override
    public void onMessage(String message) {
        logger.debug("received message: {}", message);
        try {
            client.callFun(message);
        } catch (Exception e) {
            logger.error("Error processing message (SDK or callback threw)", e);
        }
    }

    @Override
    public void onMessage(ByteBuffer message) {
        logger.debug("received ByteBuffer");
    }

    @Override
    public void onError(Exception ex) {
        logger.error("an error occurred", ex);
    }

}