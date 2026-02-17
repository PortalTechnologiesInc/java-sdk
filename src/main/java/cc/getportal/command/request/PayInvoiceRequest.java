package cc.getportal.command.request;

import cc.getportal.command.PortalRequest;
import cc.getportal.command.notification.UnitNotification;
import cc.getportal.command.response.PayInvoiceResponse;

public class PayInvoiceRequest extends PortalRequest<PayInvoiceResponse, UnitNotification> {

    private final String invoice;

    public PayInvoiceRequest(String invoice) {
        this.invoice = invoice;
    }

    @Override
    public String name() {
        return "PayInvoice";
    }

    @Override
    public Class<PayInvoiceResponse> responseType() {
        return PayInvoiceResponse.class;
    }
}
