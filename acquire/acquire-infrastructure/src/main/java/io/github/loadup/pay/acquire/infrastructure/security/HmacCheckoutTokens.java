/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.infrastructure.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.acquire.app.port.CheckoutTokens;
import io.github.loadup.pay.acquire.domain.model.CheckoutSession;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.acquire.infrastructure.config.CheckoutProperties;
import io.github.loadup.pay.common.BusinessTime;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAVAILABLE;

@Component
public class HmacCheckoutTokens implements CheckoutTokens {
    private final CheckoutProperties properties;
    private final SecureRandom random = new SecureRandom();

    public HmacCheckoutTokens(CheckoutProperties properties) {
        this.properties = properties;
    }

    @Override
    public String keyRef() {
        return properties.keyRef();
    }

    @Override
    public String activeKeyVersion() {
        return properties.activeKeyVersion();
    }

    @Override
    public String nonce() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public String issue(CheckoutSession session, String nonce, String keyRef, String keyVersion) {
        String secret = properties.keys().get(keyVersion);
        if (!properties.keyRef().equals(keyRef)
                || secret == null
                || session.expiresAt().getNano() != 0
                || !session.id().matches("[A-Za-z0-9_-]{1,64}")
                || !nonce.matches("[0-9a-f]{64}")) throw new CommonException(SESSION_UNAVAILABLE);
        String data = "pay-checkout-v1\n" + session.id() + "\n" + session.orderId() + "\n"
                + BusinessTime.external(session.expiresAt()).toEpochSecond() + "\n" + nonce + "\n";
        byte[] mac = HexFormat.of().parseHex(DigestUtils.hmacSha256(data, secret));
        return session.id() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    @Override
    public String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    @Override
    public boolean matches(String token, StoredCheckoutSession stored) {
        if (token == null
                || token.length() > 108
                || !token.matches("[A-Za-z0-9_-]{1,64}\\.[A-Za-z0-9_-]{43}")
                || !token.startsWith(stored.session().id() + ".")) return false;
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.US_ASCII),
                stored.tokenHash().getBytes(StandardCharsets.US_ASCII));
    }
}
