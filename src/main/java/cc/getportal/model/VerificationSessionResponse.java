package cc.getportal.model;

/**
 * Response from {@code POST /verification/sessions}.
 */
public class VerificationSessionResponse {
    public String session_id;
    public String session_url;
    public String ephemeral_npub;
    public long expires_at;
    public String stream_id;
}
