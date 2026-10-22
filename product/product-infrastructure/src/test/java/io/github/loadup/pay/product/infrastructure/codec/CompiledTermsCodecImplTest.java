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
package io.github.loadup.pay.product.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.modules.contract.app.support.ContractCodec;
import io.github.loadup.modules.contract.domain.model.ConfigurationLayer;
import io.github.loadup.modules.contract.domain.model.ValueOrigin;
import io.github.loadup.pay.common.AccessMode;
import io.github.loadup.pay.common.Channel;
import io.github.loadup.pay.common.ContractReference;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.common.PaymentMethod;
import io.github.loadup.pay.product.app.model.CompiledAcquiringTerms;
import io.github.loadup.pay.product.app.model.EncodedAcquiringTerms;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO;
import io.github.loadup.pay.product.client.dto.FeeTermsDTO;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import io.github.loadup.pay.product.client.dto.PaymentTermsDTO;
import io.github.loadup.pay.product.client.dto.RefundTermsDTO;
import io.github.loadup.pay.product.client.enums.FeeRefundPolicy;
import io.github.loadup.pay.product.client.enums.RefundAllocationPolicy;
import io.github.loadup.pay.product.client.enums.RefundAmountRestorePolicy;
import io.github.loadup.pay.product.client.enums.RefundCountRestorePolicy;
import io.github.loadup.pay.product.client.enums.UnpaidOrderAction;
import io.github.loadup.pay.product.domain.model.AdmissionIntent;
import io.github.loadup.pay.product.domain.model.OrderAdmission;
import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompiledTermsCodecImplTest {
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    private final CompiledTermsCodecImpl codec =
            new CompiledTermsCodecImpl(new ContractCodec(mapper), mapper, validation.getValidator());

    @AfterEach
    void close() {
        validation.close();
    }

    private AcquiringTermsDTO terms(String rate, List<ChannelCapabilityDTO> capabilities) {
        return new AcquiringTermsDTO(
                1,
                new OrderTermsDTO(600, 3600, UnpaidOrderAction.KEEP_PENDING, false, 0, 300),
                new PaymentTermsDTO(1, 100000, null, true),
                new FeeTermsDTO(new BigDecimal(rate), 0, RoundingMode.HALF_UP, 0, null, FeeRefundPolicy.NO_RETURN),
                new RefundTermsDTO(true, true, 86400, RefundAllocationPolicy.FIFO),
                new NotificationTermsDTO(false, null, null, 1000, 1, BigDecimal.valueOf(2), 60, 5, 3600),
                new LimitPolicyRefDTO(
                        "policy",
                        1,
                        "a".repeat(64),
                        false,
                        null,
                        RefundAmountRestorePolicy.NO_RESTORE,
                        RefundCountRestorePolicy.NO_RESTORE),
                capabilities,
                "routing-v1",
                "RETURN_V1");
    }

    private List<ChannelCapabilityDTO> capabilities() {
        return List.of(
                new ChannelCapabilityDTO(
                        Channel.WECHAT, AccessMode.DIRECT, Set.of(PaymentMethod.H5, PaymentMethod.JSAPI)),
                new ChannelCapabilityDTO(Channel.ALIPAY, AccessMode.SERVICE_PROVIDER, Set.of(PaymentMethod.H5)));
    }

    private CompiledAcquiringTerms compiled(AcquiringTermsDTO terms, String contract) {
        return new CompiledAcquiringTerms(
                1,
                contract,
                1,
                "original-snapshot",
                terms,
                Map.of("fee.rate", new ValueOrigin(ConfigurationLayer.PRODUCT, "product", 1)));
    }

    private StoredOrderAdmission stored(EncodedAcquiringTerms encoded, String contract) {
        var at = LocalDateTime.of(2026, 10, 21, 10, 0);
        var intent = new AdmissionIntent(
                "order", "merchant", "scope", "a".repeat(64), DomainChecks.amount(100, "CNY"), null, "b".repeat(64));
        var admission = new OrderAdmission(
                "admission",
                "order",
                "merchant",
                "a".repeat(64),
                io.github.loadup.pay.product.domain.model.AdmissionDecision.ADMITTED,
                "ALLOW",
                new ContractReference(contract, 1, 1, "original-snapshot", encoded.hash()),
                at,
                at.plusMinutes(5));
        return new StoredOrderAdmission(intent, admission, 1, 1, encoded.json());
    }

    @Test
    void roundTripPreservesExactCommercialRates() {
        var original = compiled(terms("0.0030000000000000000000000001", capabilities()), "contract");
        var encoded = codec.encode(original);
        assertEquals(
                original.terms().fee().rate(),
                codec.decode(stored(encoded, "contract")).terms().fee().rate());
    }

    @Test
    void distinctHighPrecisionRatesCannotCollapseToTheSameHash() {
        var first = codec.encode(compiled(terms("0.0030000000000000000000000001", capabilities()), "contract"));
        var second = codec.encode(compiled(terms("0.0030000000000000000000000002", capabilities()), "contract"));
        assertNotEquals(first.hash(), second.hash());
    }

    @Test
    void unorderedCapabilitiesHaveAStableHash() {
        var order = capabilities();
        var reversed = List.of(order.getLast(), order.getFirst());
        assertEquals(
                codec.encode(compiled(terms("0.003", order), "contract")).hash(),
                codec.encode(compiled(terms("0.003", reversed), "contract")).hash());
    }

    @Test
    void alteredTermsOrAReboundContractAreRejected() {
        var encoded = codec.encode(compiled(terms("0.003", capabilities()), "contract"));
        String changed = encoded.json().replace("\"waitSeconds\":600", "\"waitSeconds\":601");
        assertNotEquals(encoded.json(), changed);
        assertThrows(
                CommonException.class,
                () -> codec.decode(stored(new EncodedAcquiringTerms(encoded.hash(), changed), "contract")));
        assertThrows(CommonException.class, () -> codec.decode(stored(encoded, "another-contract")));
    }
}
