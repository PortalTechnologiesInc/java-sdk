package cc.getportal.model;

import com.google.gson.annotations.SerializedName;

public class PayInvoiceResponse {
    @SerializedName("payment_hash")
    public String paymentHash;
    @SerializedName("fees_paid")
    public long feesPaid;
}
