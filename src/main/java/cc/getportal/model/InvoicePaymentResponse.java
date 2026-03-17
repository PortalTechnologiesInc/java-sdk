package cc.getportal.model;

import com.google.gson.annotations.SerializedName;

public class InvoicePaymentResponse {
    public String invoice;
    @SerializedName("payment_hash")
    public String paymentHash;
}
