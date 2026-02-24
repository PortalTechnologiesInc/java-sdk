package cc.getportal.command.request;

import cc.getportal.command.PortalRequest;
import cc.getportal.command.notification.UnitNotification;
import cc.getportal.command.response.RequestInvoiceResponse;

import java.util.List;

public class RequestInvoiceRequest extends PortalRequest<RequestInvoiceResponse, UnitNotification> {

    private final String recipient_key;
    private final List<String> subkeys;
    private final RequestInvoiceParams content;

    public RequestInvoiceRequest(String recipientKey, List<String> subkeys, RequestInvoiceParams content) {
        recipient_key = recipientKey;
        this.subkeys = subkeys;
        this.content = content;
    }

    @Override
    public String name() {
        return "RequestInvoice";
    }

    @Override
    public Class<RequestInvoiceResponse> responseType() {
        return RequestInvoiceResponse.class;
    }
}
