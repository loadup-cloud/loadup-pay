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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.client.enums.LimitRestoreResult;
import io.github.loadup.pay.payment.app.converter.RefundSettlementConverter;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.app.port.RefundProjectionEnvelopeCodec;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundLimitDeliveryTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundSettlementConverter.class.getPackageName());
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final RefundProjectionEnvelopeCodec codec = mock(RefundProjectionEnvelopeCodec.class);
    final PaymentLimitClient client = mock(PaymentLimitClient.class);
    final RefundSettlementConverter converter = context.getBean(RefundSettlementConverter.class);
    final RefundLimitDelivery delivery = new RefundLimitDelivery(outbox, codec, client, converter);
    final EventEnvelope<RefundOutcomeEventDTO> envelope = new EventEnvelope<>("delivery-limit", "business-limit", "payment",
            RefundProjectionEvents.LIMIT, 1, "refund", 2, BusinessTime.external(RefundResultFixtures.NOW),
            new RefundOutcomeEventDTO("refund", "request", "payment", "order", "operation", "merchant", 1000, "CNY",
                    RefundState.SUCCEEDED, 2, 1000, "refund-evidence"));
    final OutboxEvent event = new OutboxEvent("queue-limit",
            new OutboxMessage("tenant", RefundProjectionEvents.LIMIT, 1, "refund", "limit-payload"),
            RefundResultFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), null);

    @BeforeEach
    void setup() {
        when(outbox.find("tenant", "queue-limit")).thenReturn(Optional.of(
                new OutboxRecord("queue-limit", "tenant", RefundProjectionEvents.LIMIT, "refund", "PROCESSING", 1, null)));
        when(codec.limit("limit-payload")).thenReturn(envelope);
        when(client.supportsTenant("tenant")).thenReturn(true);
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(validAck());
    }

    @AfterEach
    void close() {
        context.close();
    }

    LimitRestoreDTO validAck() {
        return new LimitRestoreDTO("operation", "payment", "refund", 2, "business-limit", "refund-evidence",
                1000, 1200, 4, LimitRestoreResult.RESTORED, 700, true);
    }

    void deliver() {
        TenantUtil.runWithTenant("tenant", () -> delivery.deliver(event));
    }

    @Test
    void retriesRestoreTheOriginalCommandAndRejectMismatchedAcknowledgements() {
        deliver();
        deliver();
        LimitRefundRestoreCommand command = converter.toRestore(envelope);
        verify(client, times(2)).restoreRefund("tenant", command);

        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("other", "payment", "refund", 2,
                "business-limit", "refund-evidence", 1000, 1200, 4, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "other-payment", "refund", 2,
                "business-limit", "refund-evidence", 1000, 1200, 4, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "other-refund", 2,
                "business-limit", "refund-evidence", 1000, 1200, 4, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "refund", 3,
                "business-limit", "refund-evidence", 1000, 1200, 4, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "refund", 2,
                "other-business-event", "refund-evidence", 1000, 1200, 4, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "refund", 2,
                "business-limit", "wrong-evidence", 1000, 1200, 4, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "refund", 2,
                "business-limit", "refund-evidence", 900, 1200, 4, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "refund", 2,
                "business-limit", "refund-evidence", 1000, 900, 4, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "refund", 2,
                "business-limit", "refund-evidence", 1000, 1200, 0, LimitRestoreResult.RESTORED, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "refund", 2,
                "business-limit", "refund-evidence", 1000, 1200, 4, LimitRestoreResult.RESTORED, 1300, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(client.restoreRefund(eq("tenant"), any())).thenReturn(new LimitRestoreDTO("operation", "payment", "refund", 2,
                "business-limit", "refund-evidence", 1000, 1200, 4, LimitRestoreResult.NO_ACTION, 700, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
    }

    @Test
    void wrongTenantAndUnclaimedOrNonProcessingQueueCannotCallLimitCenter() {
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("other", () -> delivery.deliver(event)));
        when(outbox.find("tenant", "queue-limit")).thenReturn(Optional.empty());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(outbox.find("tenant", "queue-limit")).thenReturn(Optional.of(
                new OutboxRecord("queue-limit", "tenant", RefundProjectionEvents.LIMIT, "refund", "PENDING", 1, null)));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::deliver).getResultCode());
        verify(client, never()).restoreRefund(eq("tenant"), any());
    }
}
