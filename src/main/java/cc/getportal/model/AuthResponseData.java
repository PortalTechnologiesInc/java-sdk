package cc.getportal.model;

import com.google.gson.annotations.SerializedName;

public class AuthResponseData {
    @SerializedName("user_key")
    public String userKey;
    public String recipient;
    public String challenge;
    public AuthResponseStatus status;
}
