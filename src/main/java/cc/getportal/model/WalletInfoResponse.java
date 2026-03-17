package cc.getportal.model;

import com.google.gson.annotations.SerializedName;

public class WalletInfoResponse {
    @SerializedName("balance_msat")
    public long balanceMsat;
    @SerializedName("node_pubkey")
    public String nodePubkey;
    @SerializedName("node_alias")
    public String nodeAlias;
}
