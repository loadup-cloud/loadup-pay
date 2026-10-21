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
package io.github.loadup.pay.product.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.modules.contract.domain.model.TypedValue;
import io.github.loadup.pay.product.app.model.ActivatedLimitPolicy;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcquiringTermsCompilerTest {
    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final AcquiringTermsCompiler compiler = new AcquiringTermsCompiler(factory.getValidator());
    private final ActivatedLimitPolicy policy =
            new ActivatedLimitPolicy("policy", 1, "a".repeat(64), "ACTIVE", LocalDateTime.of(2026, 10, 21, 0, 0));

    @AfterEach
    void close() {
        factory.close();
    }

    @Test
    void compilesFixedTotalTermsAndPreservesVersionedRoutingReferences() {
        var terms = compiler.compile(AcquiringFixtures.configuration(AcquiringFixtures.values()), policy);
        assertEquals("routing-v1", terms.routingProfileRef());
        assertEquals("RETURN_V1", terms.returnTargetCode());
        assertEquals("a".repeat(64), terms.limits().policyHash());
        assertNull(terms.payment().maxSuccessfulPayments());
        assertEquals(600, terms.order().waitSeconds());
    }

    @Test
    void unknownPathsAndWrongScalarTypesAreRejected() {
        var values = AcquiringFixtures.values();
        values.put("order.allowReopen", TypedValue.bool(true));
        assertThrows(CommonException.class, () -> compiler.compile(AcquiringFixtures.configuration(values), policy));
        values.remove("order.allowReopen");
        values.put("order.waitSeconds", TypedValue.text("600"));
        assertThrows(CommonException.class, () -> compiler.compile(AcquiringFixtures.configuration(values), policy));
    }

    @Test
    void unsupportedRefundOrderingAndAutomaticLateCollectionAreRejected() {
        var values = AcquiringFixtures.values();
        values.put("refund.allocationPolicy", TypedValue.text("LIFO"));
        assertThrows(CommonException.class, () -> compiler.compile(AcquiringFixtures.configuration(values), policy));
        values.put("refund.allocationPolicy", TypedValue.text("FIFO"));
        values.put("refund.lateCollectionPolicy", TypedValue.text("VERIFIED_AUTO_REFUND"));
        assertThrows(CommonException.class, () -> compiler.compile(AcquiringFixtures.configuration(values), policy));
    }

    @Test
    void enabledZeroThresholdIsValidAndMissingThresholdIsRejected() {
        var values = AcquiringFixtures.values();
        values.put("limit.MONTH.AMOUNT.enabled", TypedValue.bool(true));
        assertThrows(CommonException.class, () -> compiler.compile(AcquiringFixtures.configuration(values), policy));
        values.put("limit.MONTH.AMOUNT.threshold", TypedValue.integer(0));
        assertDoesNotThrow(() -> compiler.compile(AcquiringFixtures.configuration(values), policy));
    }

    @Test
    void parentAggregationRequiresAndRetainsItsControlledPolicyReference() {
        var values = AcquiringFixtures.values();
        values.put("limit.parentAggregationEnabled", TypedValue.bool(true));
        assertThrows(CommonException.class, () -> compiler.compile(AcquiringFixtures.configuration(values), policy));
        values.put("limit.parentPolicyRef", TypedValue.text("parent-v1"));
        assertEquals(
                "parent-v1",
                compiler.compile(AcquiringFixtures.configuration(values), policy)
                        .limits()
                        .parentPolicyRef());
    }

    @Test
    void mismatchedActivationReceiptCannotBecomeCompiledTerms() {
        var other = new ActivatedLimitPolicy("different", 1, "a".repeat(64), "ACTIVE", policy.activatedAt());
        var failure = assertThrows(
                CommonException.class,
                () -> compiler.compile(AcquiringFixtures.configuration(AcquiringFixtures.values()), other));
        assertSame(ProductResultCodeEnum.UNSUPPORTED_POLICY, failure.getResultCode());
    }

    @Test
    void aRuleWithoutAnyAllowedPaymentMethodCannotBePublished() {
        var values = AcquiringFixtures.values();
        values.put("channel.WECHAT.DIRECT.QR.enabled", TypedValue.bool(false));
        assertThrows(CommonException.class, () -> compiler.compile(AcquiringFixtures.configuration(values), policy));
    }
}
