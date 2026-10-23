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
package io.github.loadup.pay.acquire.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentPolicy;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentInitiationStorageConverter;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentInitiationPersistenceTest {
    static final long AMOUNT = 9007199254740993L;
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final OrderCreationCodecImpl hashes = new OrderCreationCodecImpl(mapper, validation.getValidator());
    final PaymentInitiationCodecImpl codec = new PaymentInitiationCodecImpl(mapper, validation.getValidator(), hashes);
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentInitiationStorageConverter.class.getPackageName());
    final PaymentInitiationStorageConverter converter = context.getBean(PaymentInitiationStorageConverter.class);

    static AcquiringTermsDTO terms() {
        return new AcquiringTermsDTO(
                1,
                new OrderTermsDTO(
                        600, 3600, io.github.loadup.pay.product.client.enums.UnpaidOrderAction.CLOSE, false, 0, 300),
                new PaymentTermsDTO(1, Long.MAX_VALUE, null, true),
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
        context.close();
        validation.close();
    }

    PaymentExecuteCommand command() {
        return new PaymentExecuteCommand(
                "payment",
                "order",
                "allocation",
                "admission",
                "merchant",
                AMOUNT,
                "CNY",
                "a".repeat(64),
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5),
                null,
                new AdmittedOrderTermsDTO(
                        "admission",
                        "order",
                        "merchant",
                        AMOUNT,
                        "CNY",
                        "contract",
                        1,
                        1,
                        "snapshot",
                        "b".repeat(64),
                        terms()));
    }

    StoredPaymentAllocation stored(String payload, String hash) {
        var allocation = new OrderPaymentAllocation(
                "allocation",
                "order",
                "payment",
                "merchant/" + "c".repeat(64),
                "action",
                "a".repeat(64),
                DomainChecks.cnyAmount(AMOUNT, "CNY"),
                AllocationState.HELD,
                0);
        return new StoredPaymentAllocation(allocation, "c".repeat(64), payload, hash, 0);
    }

    @Test
    void exactLongAmountsAndFrozenTermsRoundTripThroughJsonAndStorage() {
        String payload = codec.encode(command());
        var value = stored(payload, hashes.checksum(payload));
        assertEquals(command(), codec.decode(value));
        var row = converter.toAllocationDO("tenant", value);
        assertEquals("tenant", row.getTenantId());
        assertEquals(0, row.getDeleted());
        assertNull(row.getCreatedAt());
        var restored = converter.toStored(row, converter.toAllocation(row));
        assertEquals(value, restored);
        assertEquals(command(), codec.decode(restored));
        assertFalse(value.toString().contains("routing-v1"));
        assertFalse(row.toString().contains("routing-v1"));
    }

    @Test
    void corruptedPayloadOrValidHashWithChangedBindingCannotBeReplayed() {
        String original = codec.encode(command());
        String changed = original.replace("allocation", "foreign-allocation");
        assertNotEquals(original, changed);
        assertThrows(CommonException.class, () -> codec.decode(stored(changed, hashes.checksum(original))));
        assertThrows(CommonException.class, () -> codec.decode(stored(changed, hashes.checksum(changed))));
    }

    @Test
    void unknownFieldsAndSensitivePreparedReferencesCannotEnterThisH5Protocol() {
        String original = codec.encode(command());
        String unknown = "{\"unexpected\":true," + original.substring(1);
        assertThrows(CommonException.class, () -> codec.decode(stored(unknown, hashes.checksum(unknown))));
        var value = command();
        String prepared = codec.encode(new PaymentExecuteCommand(
                value.paymentId(),
                value.orderId(),
                value.allocationId(),
                value.admissionId(),
                value.payeeMerchantId(),
                value.amountMinor(),
                value.currency(),
                value.requestDigest(),
                value.choice(),
                "synthetic-input",
                value.admittedTerms()));
        assertThrows(CommonException.class, () -> codec.decode(stored(prepared, hashes.checksum(prepared))));
    }

    @Test
    void reservationPatchDoesNotOverwriteDeadlineVersionAdmissionOrSuccessFacts() {
        var order = new AcquireOrder(
                "order",
                "merchant",
                DomainChecks.cnyAmount(10000, "CNY"),
                2000,
                3000,
                1,
                1,
                AcquireOrderState.PENDING,
                LocalDateTime.of(2026, 10, 22, 12, 0),
                UnpaidOrderAction.CLOSE,
                new OrderPaymentPolicy(1, 10000, null, true),
                0,
                7);
        var patch = converter.toReservationPatch(order);
        assertEquals(3000L, patch.getPaymentReservedMinor());
        assertEquals(1, patch.getReservedPaymentCount());
        assertEquals("PENDING", patch.getState());
        assertEquals(7L, patch.getRowVersion());
        assertNull(patch.getDeadlineVersion());
        assertNull(patch.getOrderDueAt());
        assertNull(patch.getAdmissionId());
        assertNull(patch.getPaidAmountMinor());
        assertNull(patch.getSuccessfulPaymentCount());
        assertNull(patch.getPaidAt());
        assertNull(patch.getClosedAt());
        assertNull(patch.getCloseEpoch());
        assertNull(patch.getId());
    }
}
