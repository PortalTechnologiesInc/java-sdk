package cc.getportal.command.request;

import cc.getportal.command.PortalRequest;
import cc.getportal.command.notification.UnitNotification;
import cc.getportal.command.response.GetWalletInfoResponse;

public class GetWalletInfoRequest extends PortalRequest<GetWalletInfoResponse, UnitNotification> {

    public GetWalletInfoRequest() {
    }

    @Override
    public String name() {
        return "GetWalletInfo";
    }

    @Override
    public Class<GetWalletInfoResponse> responseType() {
        return GetWalletInfoResponse.class;
    }

    @Override
    public boolean isUnit() {
        return true;
    }
}
