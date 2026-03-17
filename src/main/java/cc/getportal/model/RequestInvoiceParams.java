package cc.getportal.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

public class RequestInvoiceParams {
    public long amount;
    public Currency currency;
    public String description;
    @SerializedName("subscription_id")
    @Nullable public String subscriptionId;
    @SerializedName("auth_token")
    @Nullable public String authToken;
    @SerializedName("request_id")
    @Nullable public String requestId;
    @SerializedName("expires_at")
    @Nullable public Long expiresAt;

    public RequestInvoiceParams(long amount, Currency currency, String description) {
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }

    public RequestInvoiceParams(long amount, Currency currency, String description,
                                @Nullable String subscriptionId, @Nullable String authToken,
                                @Nullable String requestId, @Nullable Long expiresAt) {
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.subscriptionId = subscriptionId;
        this.authToken = authToken;
        this.requestId = requestId;
        this.expiresAt = expiresAt;
    }
}
