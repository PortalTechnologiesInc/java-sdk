package cc.getportal.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class KeyHandshakeResult {
    @SerializedName("main_key")
    public String mainKey;
    @SerializedName("preferred_relays")
    public List<String> preferredRelays;
}
