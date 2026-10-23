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
package io.github.loadup.pay.payment.infrastructure.converter;

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.domain.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RefundStorageConverterTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundStorageConverter.class.getPackageName());
    final RefundStorageConverter converter = context.getBean(RefundStorageConverter.class);
    final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0, 0, 123456000);

    StoredRefundRequest stored() {
        var child = RefundOrder.init("refund", "request", "payment", DomainChecks.cnyAmount(4000, "CNY"), "a".repeat(32));
        var original = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "revision", "b".repeat(64), "original-request");
        var allocation = new StoredRefundChild(child, 1, original, "original-transaction", now.minusHours(1));
        var request = RefundRequest.init("request", "order", "merchant", "R001", child.amount(), List.of(child));
        return new StoredRefundRequest(request, "CUSTOMER_REQUEST", "c".repeat(64), "d".repeat(64), "admission", "{}", "e".repeat(64), now, List.of(allocation));
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void roundTripsTheOriginalPaymentIdentityAndFrozenPlanWithoutAuditTimeSubstitution() {
        var value = stored();
        var child = value.allocations().getFirst();
        var header = converter.toRequestDO("tenant", value);
        var childRow = converter.toChildDO("tenant", child);
        var planRow = converter.toPlanDO("tenant", child);
        assertEquals("tenant", header.getTenantId());
        assertEquals("FIFO", header.getAllocationPolicy());
        assertEquals("INIT", header.getState());
        assertEquals(0L, header.getRowVersion().longValue());
        assertNull(header.getCreatedAt());
        assertNull(header.getUpdatedAt());
        assertEquals("original-transaction", childRow.getOriginalChannelTransactionNo());
        assertEquals("original-request", childRow.getOriginalChannelRequestNo());
        assertNull(childRow.getSucceededAt());
        assertNull(childRow.getChannelRefundTransactionNo());
        assertEquals(0L, childRow.getQuerySequence().longValue());
        assertEquals("refund", planRow.getRefundId());
        assertEquals(1, planRow.getSequenceNo().intValue());
        assertEquals(4000, planRow.getAmountMinor().longValue());
        var restoredChild = converter.toChild(childRow, converter.toRefund(childRow), converter.toIdentity(childRow), planRow.getSequenceNo());
        var request = converter.toRequest(header, List.of(restoredChild.refund()));
        assertEquals(value, converter.toStored(header, request, List.of(restoredChild)));
    }

    @Test
    void reservationPatchChangesOnlyHeldCapacityAndVersion() {
        var before = new PaymentRefundCapacity("payment", "order", "merchant", now, DomainChecks.cnyAmount(10000, "CNY"), 2000, 1000, 3);
        var patch = converter.toReservationPatch(before.reserve(4000));
        assertEquals(5000, patch.getReservedMinor().longValue());
        assertEquals(4, patch.getRowVersion().longValue());
        assertNull(patch.getRefundedMinor());
        assertNull(patch.getSucceededMinor());
        assertNull(patch.getPaymentId());
        assertNull(patch.getCurrency());
        assertNull(patch.getId());
        assertNull(patch.getCreatedAt());
        assertNull(patch.getUpdatedAt());
    }
}
