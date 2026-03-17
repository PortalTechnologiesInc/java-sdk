package cc.getportal.model;

import com.google.gson.annotations.SerializedName;

public class VersionResponse {
    public String version;
    @SerializedName("git_commit")
    public String gitCommit;
}
