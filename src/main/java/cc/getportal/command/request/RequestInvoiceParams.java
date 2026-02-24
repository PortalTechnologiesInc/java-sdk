package cc.getportal.command.request;

import cc.getportal.model.Currency;
import org.jetbrains.annotations.Nullable;

/**
 * Slim parameters for RequestInvoice command.
 * Server (portal-rest) computes exchange rate from amount/currency.
 * Request ID is derived from command.id.
 */
public record RequestInvoiceParams(
    long amount,
    Currency currency,
    String expires_at,
    @Nullable String description,
    @Nullable String refund_invoice
) {
}
