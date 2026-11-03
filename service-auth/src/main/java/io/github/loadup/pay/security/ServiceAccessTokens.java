package io.github.loadup.pay.security;

/** Application credentials only. Merchant/user delegation retains the original actor token. */
@FunctionalInterface
public interface ServiceAccessTokens {
    String token(String tenant, String service, String audience);
}
