package cc.getportal.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AuthResponseStatus {
    /** "approved" or "declined" */
    public String status;
    public String reason;
    @SerializedName("granted_permissions")
    public List<String> grantedPermissions;
    @SerializedName("session_token")
    public String sessionToken;
}
