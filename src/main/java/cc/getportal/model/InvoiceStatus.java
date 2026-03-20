package cc.getportal.model;

import org.jetbrains.annotations.Nullable;

/**
 * Terminal status of a single or raw payment request.
 * Delivered as the terminal event payload for {@code payment_status_update} streams.
 */
public class InvoiceStatus {
    /** One of: {@code paid}, {@code timeout}, {@code error},
     *  {@code user_approved}, {@code user_success}, {@code user_failed}, {@code user_rejected}. */
    public String status;
    @Nullable public String preimage;
    @Nullable public String reason;
}
