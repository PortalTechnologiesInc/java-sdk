package cc.getportal;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps an async operation: the stream ID is available immediately,
 * while the {@code done} future resolves when a terminal event arrives.
 */
public class AsyncOperation<T> {
    private final String streamId;
    private final CompletableFuture<T> done;
    private final Map<String, String> metadata = new ConcurrentHashMap<>();

    public AsyncOperation(String streamId, CompletableFuture<T> done) {
        this.streamId = streamId;
        this.done = done;
    }

    public String streamId() { return streamId; }
    public CompletableFuture<T> done() { return done; }

    public void setMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public @Nullable String getMetadata(String key) {
        return metadata.get(key);
    }

    /** Convenience: get the session_url for verification sessions. */
    public @Nullable String sessionUrl() {
        return metadata.get("session_url");
    }

    /** Convenience: get the session_id for verification sessions. */
    public @Nullable String sessionId() {
        return metadata.get("session_id");
    }
}
