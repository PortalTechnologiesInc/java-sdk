package cc.getportal;

import com.google.gson.JsonObject;

import java.util.Set;

/**
 * A single event from a portal-rest async stream ({@code GET /events/:streamId}).
 * <p>
 * The {@code type} field identifies the event kind. Use {@link #isTerminal()} to
 * detect whether no more events are expected for the stream.
 * The full raw JSON is available internally for deserialization but is not part
 * of the public API.
 */
public class StreamEvent {

    public String type;
    public int index;
    public String timestamp;

    /** Full raw JSON — package-private, used by {@link PortalClient} for typed deserialization. */
    transient JsonObject _raw;

    // ---- Terminal type detection ----

    static final Set<String> TERMINAL_TYPES = Set.of(
            "key_handshake",
            "authenticate_key",
            "recurring_payment_response",
            "invoice_response",
            "cashu_response",
            "error"
    );

    static final Set<String> TERMINAL_PAYMENT_STATUSES = Set.of(
            "paid", "timeout", "error", "user_success", "user_failed", "user_rejected"
    );

    /**
     * Returns {@code true} if this event is terminal — i.e. no further events
     * are expected on the stream after this one.
     */
    public boolean isTerminal() {
        if (type == null) return false;
        if (TERMINAL_TYPES.contains(type)) return true;
        if ("payment_status_update".equals(type) && _raw != null) {
            try {
                JsonObject status = _raw.getAsJsonObject("status");
                if (status != null && status.has("status")) {
                    return TERMINAL_PAYMENT_STATUSES.contains(status.get("status").getAsString());
                }
            } catch (Exception ignored) { /* not terminal */ }
        }
        return false;
    }

    @Override
    public String toString() {
        return "StreamEvent{type='" + type + "', index=" + index + "}";
    }
}
