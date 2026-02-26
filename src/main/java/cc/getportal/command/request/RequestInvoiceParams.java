package cc.getportal.command.request;

import cc.getportal.model.Currency;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * Slim parameters for RequestInvoice command.
 * Server (portal-rest) computes exchange rate from amount/currency.
 * Request ID is derived from command.id.
 */
public record RequestInvoiceParams(
    long amount,
    Currency currency,
    @SerializedName("expires_at")
    String expiresAt,
    @Nullable String description,
    @Nullable String refund_invoice,
    /** Optional request ID. If not provided, the command ID is used by the server. */
    @Nullable String request_id
) {

    public RequestInvoiceParams(long amount, Currency currency, long expiresAt, @Nullable String description, @Nullable String refund_invoice) {
        this(amount, currency, String.valueOf(expiresAt), description, refund_invoice, null);
    }

    public RequestInvoiceParams(long amount, Currency currency, long expiresAt, @Nullable String description, @Nullable String refund_invoice, @Nullable String requestId) {
        this(amount, currency, String.valueOf(expiresAt), description, refund_invoice, requestId);
    }
}
