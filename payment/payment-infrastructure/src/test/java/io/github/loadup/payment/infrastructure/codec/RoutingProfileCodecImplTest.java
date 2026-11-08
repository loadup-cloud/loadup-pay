/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.payment.infrastructure.codec;

import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.payment.app.converter.RoutingProfileConverter;
import io.github.loadup.payment.domain.model.RoutingProfileSpec;
import io.github.loadup.payment.domain.model.RoutingRule;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class RoutingProfileCodecImplTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(RoutingProfileConverter.class.getPackageName());
    final RoutingProfileCodecImpl codec = new RoutingProfileCodecImpl(
            JsonMapper.builder().build(), context.getBean(RoutingProfileConverter.class), validation.getValidator());

    RoutingRule rule(String code) {
        return new RoutingRule(code, Channel.WECHAT, AccessMode.DIRECT, PaymentMethod.H5);
    }

    RoutingProfileSpec spec(List<RoutingRule> rules) {
        return new RoutingProfileSpec("routing-v1", "merchant", rules);
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void canonicalRoundTripPreservesOrderedRules() {
        var spec = spec(List.of(rule("first"), rule("second")));
        String payload = codec.encode(spec);
        assertEquals(spec, codec.decode(payload, codec.checksum(payload)));
    }

    @Test
    void whitespaceAndObjectKeyOrderingDoNotChangeTheDefinitionHash() {
        var spec = spec(List.of(rule("first")));
        String reordered = """
                { "rules": [{"method":"H5","accessMode":"DIRECT","channel":"WECHAT","bindingCode":"first"}],
                  "payeeMerchantId":"merchant", "profileRef":"routing-v1" }
                """;
        assertEquals(spec, codec.decode(reordered, codec.checksum(codec.encode(spec))));
    }

    @Test
    void ruleOrderAndPayeeAreProtectedByTheHash() {
        var original = spec(List.of(rule("first"), rule("second")));
        String hash = codec.checksum(codec.encode(original));
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(
                                CommonException.class,
                                () -> codec.decode(codec.encode(spec(List.of(rule("second"), rule("first")))), hash))
                        .getResultCode());
        var otherPayee = new RoutingProfileSpec("routing-v1", "other-merchant", original.rules());
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> codec.decode(codec.encode(otherPayee), hash))
                        .getResultCode());
    }

    @Test
    void unknownFieldsAreNotSilentlyDiscarded() {
        var original = spec(List.of(rule("first")));
        String payload = codec.encode(original);
        String extra = payload.substring(0, payload.length() - 1) + ",\"fallbackToAnyBinding\":true}";
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> codec.decode(extra, codec.checksum(payload)))
                        .getResultCode());
    }

    @Test
    void requestDigestSeparatesTenantCallerKeyAndDefinition() {
        String digest = digest("tenant", "pay-ops", "publish", "a".repeat(64));
        assertNotEquals(digest, digest("other-tenant", "pay-ops", "publish", "a".repeat(64)));
        assertNotEquals(digest, digest("tenant", "other-service", "publish", "a".repeat(64)));
        assertNotEquals(digest, digest("tenant", "pay-ops", "other-key", "a".repeat(64)));
        assertNotEquals(digest, digest("tenant", "pay-ops", "publish", "b".repeat(64)));
    }

    private String digest(String tenant, String caller, String key, String contentHash) {
        return TenantUtil.callWithTenant(tenant, () -> codec.requestDigest(caller, key, contentHash));
    }
}
