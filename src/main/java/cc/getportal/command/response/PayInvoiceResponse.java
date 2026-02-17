package cc.getportal.command.response;

import cc.getportal.command.PortalResponse;

public record PayInvoiceResponse(
    String preimage
) implements PortalResponse {

}
