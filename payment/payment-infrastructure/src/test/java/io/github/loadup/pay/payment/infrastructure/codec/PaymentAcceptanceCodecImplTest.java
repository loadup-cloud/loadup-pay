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
package io.github.loadup.pay.payment.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
import io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO;
import io.github.loadup.pay.product.client.dto.FeeTermsDTO;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import io.github.loadup.pay.product.client.dto.PaymentTermsDTO;
import io.github.loadup.pay.product.client.dto.RefundTermsDTO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentIntentSnapshotConverter;
import io.github.loadup.pay.payment.infrastructure.model.PaymentIntentSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentAcceptanceCodecImplTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentIntentSnapshotConverter.class.getPackageName());
    final PaymentIntentSnapshotConverter snapshots = context.getBean(PaymentIntentSnapshotConverter.class);
    final PaymentAcceptanceCodecImpl codec = new PaymentAcceptanceCodecImpl( validation.getValidator(), snapshots);

    static AcquiringTermsDTO terms() {
        return new AcquiringTermsDTO(
                1,
                new OrderTermsDTO(
                        600, 3600, io.github.loadup.pay.product.client.enums.UnpaidOrderAction.CLOSE, false, 0, 300),
                new PaymentTermsDTO(1, 100000, null, true),
                new FeeTermsDTO(
                        new BigDecimal("0.003"),
                        0,
                        RoundingMode.HALF_UP,
                        0,
                        null,
                        io.github.loadup.pay.product.client.enums.FeeRefundPolicy.NO_RETURN),
                new RefundTermsDTO(
                        true, true, 86400, io.github.loadup.pay.product.client.enums.RefundAllocationPolicy.FIFO),
                new NotificationTermsDTO(false, null, null, 1000, 1, BigDecimal.valueOf(2), 60, 5, 3600),
                new LimitPolicyRefDTO(
                        "policy",
                        1,
                        "e".repeat(64),
                        false,
                        null,
                        io.github.loadup.pay.product.client.enums.RefundAmountRestorePolicy.NO_RESTORE,
                        io.github.loadup.pay.product.client.enums.RefundCountRestorePolicy.NO_RESTORE),
                List.of(new ChannelCapabilityDTO(Channel.WECHAT, AccessMode.DIRECT, Set.of(PaymentMethod.H5))),
                "routing-v1",
                "return-v1");
    }

    @AfterEach
    void close() {
        validation.close();
        context.close();
    }

    PaymentExecuteCommand command() {
        return new PaymentExecuteCommand(
                "payment",
                "order",
                "allocation",
                "admission",
                "merchant",
                9007199254740993L,
                "CNY",
                "a".repeat(64),
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5),
                "prepared-input",
                new AdmittedOrderTermsDTO(
                        "admission",
                        "order",
                        "merchant",
                        9007199254740993L,
                        "CNY",
                        "contract",
                        1,
                        1,
                        "snapshot",
                        "b".repeat(64),
                        terms()),
                "Payment acceptance test subject",
                OffsetDateTime.parse("2030-01-01T00:00:00+08:00"));
    }

    StoredPaymentAcceptance stored(PaymentExecuteCommand command, String payload, String hash) {
        return new StoredPaymentAcceptance(
                PaymentOrder.init(
                        command.paymentId(),
                        command.orderId(),
                        command.allocationId(),
                        command.payeeMerchantId(),
                        DomainChecks.cnyAmount(command.amountMinor(), command.currency()),
                        "operation",
                        LocalDateTime.of(2026, 10, 22, 12, 0)),
                "admission",
                "a".repeat(64),
                "acquire",
                payload,
                hash,
                null);
    }

    @Test
    void frozenCommandRoundTripsExactLongAmountsAndHidesPreparedInputInDiagnostics() {
        var value = command();
        String payload = codec.encode(value);
        var stored = stored(value, payload, codec.checksum(payload));
        assertEquals(value, codec.decode(stored));
        assertFalse(stored.toString().contains("prepared-input"));
        assertFalse(value.toString().contains("prepared-input"));
    }

    @Test
    void changedPayloadCannotPassItsOriginalChecksum() {
        var value = command();
        String payload = codec.encode(value);
        assertThrows(
                CommonException.class,
                () -> codec.decode(
                        stored(value, payload.replace("merchant", "other-merchant"), codec.checksum(payload))));
    }

    @Test
    void aValidChecksumCannotRebindThePaymentToAnotherHeader() {
        var value = command();
        String payload = codec.encode(value).replace("\"allocationId\":\"allocation\"", "\"allocationId\":\"other-allocation\"");
        assertThrows(CommonException.class, () -> codec.decode(stored(value, payload, codec.checksum(payload))));
    }

    @Test
    void unknownFieldsAndMissingRequiredContractFactsCannotBeTrusted() {
        var value = command();
        String payload = codec.encode(value);
        String extra = "{\"unsupported\":true," + payload.substring(1);
        assertThrows(CommonException.class, () -> codec.decode(stored(value, extra, codec.checksum(extra))));
        String invalid = payload.replace("\"contractId\":\"contract\"", "\"contractId\":null");
        assertNotEquals(payload, invalid);
        assertThrows(CommonException.class, () -> codec.decode(stored(value, invalid, codec.checksum(invalid))));
    }

    @Test
    void storageFormatDoesNotFollowPublicDtoJsonNamesOrTheApiNamingStrategy() {
        var value = command();
        var apiMapper = mapper.rebuild()
                .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                .addMixIn(PaymentExecuteCommand.class, RenamedCommand.class)
                .addMixIn(AdmittedOrderTermsDTO.class, RenamedAdmission.class).build();
        var apiPayload = apiMapper.writeValueAsString(value);
        org.junit.jupiter.api.Assertions.assertTrue(apiPayload.contains("apiPaymentId"));
        org.junit.jupiter.api.Assertions.assertTrue(apiPayload.contains("apiContractId"));
        var independent = new PaymentAcceptanceCodecImpl( validation.getValidator(), snapshots);
        var payload = independent.encode(value);
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("\"format\":\"" + PaymentIntentSnapshot.FORMAT + "\""));
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("\"paymentId\":\"payment\""));
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("\"contractId\":\"contract\""));
        assertFalse(payload.contains("apiPaymentId")); assertFalse(payload.contains("apiContractId"));
        assertFalse(payload.contains("payment_id")); assertFalse(payload.contains("\"choice\""));
        assertEquals(value, independent.decode(stored(value, payload, codec.checksum(payload))));
        assertFalse(snapshots.toSnapshot(value).toString().contains("prepared-input"));
    }

    @Test
    void restorationUsesFrozenStorageValidationInsteadOfCurrentPublicCommandBeanValidation() {
        var value = command();
        var payload = codec.encode(value);
        var currentPublicValidation = org.mockito.Mockito.mock(jakarta.validation.Validator.class);
        var reader = new PaymentAcceptanceCodecImpl( currentPublicValidation, snapshots);
        assertEquals(value, reader.decode(stored(value, payload, codec.checksum(payload))));
        org.mockito.Mockito.verifyNoInteractions(currentPublicValidation);
    }

    @Test
    void validChecksumsCannotMakeUnsupportedBareCrossOwnerOrLossySnapshotsReadable() {
        var value = command();
        var payload = codec.encode(value);
        var variants = java.util.List.of(
                mapper.writeValueAsString(value),
                payload.replace(PaymentIntentSnapshot.FORMAT, "unsupported.intent.v99"),
                payload.replace(PaymentIntentSnapshot.FORMAT, "pay.acquire.payment-intent.v1"),
                payload.replace("\"amountMinor\":" + value.amountMinor(), "\"amountMinor\":" + value.amountMinor() + ".5"),
                payload.replace("\"channel\":\"" + value.choice().channel().name() + "\"", "\"channel\":" + value.choice().channel().ordinal()),
                payload.replace("\"allowExactRemainderBelowMin\":true", "\"allowExactRemainderBelowMin\":null"),
                payload.replace("\"maxSuccessfulPayments\":null,", ""),
                payload.replace("\"waitSeconds\":600", "\"waitSeconds\":600,\"futurePolicy\":true"),
                payload + " {}"
        );
        for (var invalid : variants) {
            assertNotEquals(payload, invalid);
            assertThrows(CommonException.class, () -> codec.decode(stored(value, invalid, codec.checksum(invalid))));
        }
    }

    @Test
    void snapshotCopiesEveryFrozenRuleAndKeepsNestedCollectionsImmutable() {
        var original = command();
        var snapshot = snapshots.toSnapshot(original);
        assertEquals(original, snapshots.toCommand(snapshot));
        assertEquals(original.admittedTerms().terms().fee().rate(), snapshot.admittedTerms().terms().fee().rate());
        assertEquals(original.admittedTerms().terms().limits().policyHash(), snapshot.admittedTerms().terms().limits().policyHash());
        assertEquals(original.payableUntil().toInstant(), java.time.OffsetDateTime.parse(snapshot.payableUntil()).toInstant());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.admittedTerms().terms().capabilities().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.admittedTerms().terms().capabilities().getFirst().methods().clear());
    }

    abstract static class RenamedCommand {
        @com.fasterxml.jackson.annotation.JsonProperty("apiPaymentId") public abstract String paymentId();
    }
    abstract static class RenamedAdmission {
        @com.fasterxml.jackson.annotation.JsonProperty("apiContractId") public abstract String contractId();
    }

}
