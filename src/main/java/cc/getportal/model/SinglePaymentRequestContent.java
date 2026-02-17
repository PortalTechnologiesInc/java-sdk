package cc.getportal.model;

import org.jetbrains.annotations.Nullable;

public record SinglePaymentRequestContent(String description, long amount, Currency currency, @Nullable String subscription_id, @Nullable String auth_token, @Nullable String request_id) {
}
