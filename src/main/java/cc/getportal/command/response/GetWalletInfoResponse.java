package cc.getportal.command.response;

import cc.getportal.command.PortalResponse;

public record GetWalletInfoResponse(String wallet_type, long balance_msat) implements PortalResponse {
}
