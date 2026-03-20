package cc.getportal.model;

import com.google.gson.annotations.SerializedName;

/** Status of a recurring payment response: "confirmed" or "rejected". */
public class RecurringPaymentStatus {
    /** "confirmed" or "rejected" */
    public String status;

    // confirmed fields
    @SerializedName("subscription_id")
    public String subscriptionId;
    @SerializedName("authorized_amount")
    public long authorizedAmount;
    @SerializedName("authorized_currency")
    public Currency authorizedCurrency;
    @SerializedName("authorized_recurrence")
    public RecurrenceInfo authorizedRecurrence;

    // rejected fields
    public String reason;
}
