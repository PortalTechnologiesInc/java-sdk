package cc.getportal;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Payload of a portal-rest webhook POST.
 * Contains the {@code stream_id} and all event fields.
 * The full raw JSON is available internally for typed deserialization.
 */
public class WebhookPayload {

    @SerializedName("stream_id")
    public String streamId;

    public String type;
    public int index;
    public String timestamp;

    /** Full raw JSON — package-private, used by {@link PortalClient} for typed deserialization. */
    transient JsonObject _raw;

    /** Delegates terminal detection to {@link StreamEvent} logic. */
    public boolean isTerminal() {
        if (type == null) return false;
        if (StreamEvent.TERMINAL_TYPES.contains(type)) return true;
        if ("payment_status_update".equals(type) && _raw != null) {
            try {
                JsonObject status = _raw.getAsJsonObject("status");
                if (status != null && status.has("status")) {
                    return StreamEvent.TERMINAL_PAYMENT_STATUSES.contains(status.get("status").getAsString());
                }
            } catch (Exception ignored) { /* not terminal */ }
        }
        return false;
    }
}
