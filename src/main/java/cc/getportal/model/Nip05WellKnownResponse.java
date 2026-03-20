package cc.getportal.model;

import java.util.List;
import java.util.Map;

/**
 * Response from {@code GET /.well-known/nostr.json}.
 * Used for NIP-05 verification — maps names to public keys and optionally to relay lists.
 */
public class Nip05WellKnownResponse {
    /** Maps NIP-05 names (e.g. {@code "service"}) to hex public keys. */
    public Map<String, String> names;
    /** Optional: maps hex public keys to a list of preferred relay URLs. */
    public Map<String, List<String>> relays;
}
