package cc.getportal.model;

import com.google.gson.annotations.SerializedName;

public class RecurringPaymentResponseContent {
    @SerializedName("request_id")
    public String requestId;
    public RecurringPaymentStatus status;
}
