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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.*;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import io.github.loadup.pay.payment.app.port.RefundProjectionEnvelopeCodec;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Optional;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundMerchantDeliveryTest {
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final RefundProjectionEnvelopeCodec codec = mock(RefundProjectionEnvelopeCodec.class);
    final PaymentAcquireClient acquire = mock(PaymentAcquireClient.class);
    final RefundMerchantDelivery delivery = new RefundMerchantDelivery(outbox, codec, acquire);
    final RefundMerchantEventDTO data = new RefundMerchantEventDTO("RefundOrder", "refund", "request", "payment", "order",
            "merchant", "R001", 1000, "CNY", "FAILED", 0, 1000, 3, null);
    final EventEnvelope<RefundMerchantEventDTO> envelope = new EventEnvelope<>("delivery", "business", "payment", RefundMerchantEventDTO.TYPE,
            1, "refund", 3, BusinessTime.external(RefundResultFixtures.NOW), data);
    final OutboxEvent event = new OutboxEvent("queue", new OutboxMessage("tenant", RefundMerchantEventDTO.TYPE, 1, "refund", "payload"),
            RefundResultFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), null);
    final String merchantEventId = DigestUtils.sha256(JsonUtil.toJson(List.of("pay-merchant-event-v1", "tenant", "RefundFailed", "refund", 3L)));

    @BeforeEach void setup() {
        when(outbox.find("tenant", "queue")).thenReturn(Optional.of(new OutboxRecord("queue", "tenant", RefundMerchantEventDTO.TYPE,
                "refund", "PROCESSING", 1, null)));
        when(codec.merchant("payload")).thenReturn(envelope);
        when(acquire.supportsTenant("tenant")).thenReturn(true);
        when(acquire.acceptRefundNotification("tenant", envelope)).thenReturn(ack("ENQUEUED"));
    }
    void deliver() { TenantUtil.runWithTenant("tenant", () -> delivery.deliver(event)); }
    RefundNotificationAckDTO ack(String disposition) {
        return new RefundNotificationAckDTO("delivery", "business", "refund", 3, merchantEventId, disposition);
    }

    @Test void retriesPreserveTheOriginalSourceAndAcceptDurablyDisabledPolicies() {
        deliver(); deliver();
        when(acquire.acceptRefundNotification("tenant", envelope)).thenReturn(ack("DISABLED"));
        deliver();
        verify(acquire, times(3)).acceptRefundNotification("tenant", envelope);
        verify(acquire, never()).acceptFeeRefund(anyString(), any());
    }

    @Test void everyAcknowledgementIdentityMustMatchTheOriginalMessage() {
        for (var wrong : List.of(
                new RefundNotificationAckDTO("other", "business", "refund", 3, merchantEventId, "ENQUEUED"),
                new RefundNotificationAckDTO("delivery", "other", "refund", 3, merchantEventId, "ENQUEUED"),
                new RefundNotificationAckDTO("delivery", "business", "other", 3, merchantEventId, "ENQUEUED"),
                new RefundNotificationAckDTO("delivery", "business", "refund", 4, merchantEventId, "ENQUEUED"),
                new RefundNotificationAckDTO("delivery", "business", "refund", 3, "b".repeat(64), "ENQUEUED"))) {
            when(acquire.acceptRefundNotification("tenant", envelope)).thenReturn(wrong);
            assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        }
    }

    @Test void unclaimedQueueWrongTenantAndActiveTransactionsCannotDeliver() {
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("other", () -> delivery.deliver(event)));
        when(outbox.find("tenant", "queue")).thenReturn(Optional.empty());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::deliver).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertSame(INVALID_STATE, assertThrows(CommonException.class, this::deliver).getResultCode()); }
        finally { TransactionSynchronizationManager.clear(); }
        verifyNoInteractions(acquire);
    }
}
