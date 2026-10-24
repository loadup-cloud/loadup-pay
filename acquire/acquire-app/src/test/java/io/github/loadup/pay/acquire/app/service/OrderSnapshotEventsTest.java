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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.acquire.app.converter.OrderSnapshotConverter;
import io.github.loadup.pay.acquire.app.model.OrderSnapshotFacts;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotReader;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderSnapshotEventsTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OrderSnapshotConverter.class.getPackageName());
    final OrderSnapshotReader headers = mock(OrderSnapshotReader.class);
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final AtomicInteger ids = new AtomicInteger();
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final OrderSnapshotEvents events = new OrderSnapshotEvents(headers, context.getBean(OrderSnapshotConverter.class), outbox,
            () -> "event-" + ids.incrementAndGet(), mock(MerchantNotificationEvents.class));
    final OrderSnapshotFacts header = new OrderSnapshotFacts("order", "merchant", "M001", DomainChecks.cnyAmount(10000, "CNY"),
            4000, 2000, "PENDING", NOW.minusDays(1), NOW, 2);

    @BeforeEach
    void setup() {
        when(headers.lockCurrent("tenant", "order")).thenReturn(header);
    }

    @AfterEach
    void close() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        context.close();
    }

    void publish() {
        events.publish("tenant", "order", 2);
    }

    @Test
    void freezesThePersistedCompleteHeaderAndAuditTimesInsideTheOwningTransaction() {
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return null;
        }).when(outbox).publish(any());
        transactions.write("tenant", () -> {
            publish();
            return true;
        });
        var capture = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).publish(capture.capture());
        var message = capture.getValue();
        Map<?, ?> envelope = JsonMapper.builder().build().readValue(message.payload(), Map.class);
        var data = (Map<?, ?>) envelope.get("data");
        assertEquals("tenant", message.tenantId());
        assertEquals("order", message.businessId());
        assertEquals(OrderSnapshotEvents.TYPE, message.type());
        assertEquals("acquire", envelope.get("producer"));
        assertEquals(2, ((Number) envelope.get("aggregateVersion")).longValue());
        assertEquals(10000, ((Number) data.get("amountMinor")).longValue());
        assertEquals(4000, ((Number) data.get("paidAmountMinor")).longValue());
        assertEquals(2000, ((Number) data.get("paymentReservedMinor")).longValue());
        assertEquals("PENDING", data.get("state"));
        assertEquals("M001", data.get("merchantOrderNo"));
        assertEquals(BusinessTime.external(header.orderCreatedAt()).toInstant(),
                java.time.OffsetDateTime.parse((String) data.get("orderCreatedAt")).toInstant());
        assertEquals(BusinessTime.external(header.sourceUpdatedAt()).toInstant(),
                java.time.OffsetDateTime.parse((String) data.get("sourceUpdatedAt")).toInstant());
    }

    @Test
    void anotherPersistedVersionCannotBePublishedAsThisTransition() {
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> transactions.write("tenant", () -> {
                    events.publish("tenant", "order", 1);
                    return true;
                })).getResultCode());
        verifyNoInteractions(outbox);
    }

    @Test
    void outboxFailureAbortsTheLocalTransactionBoundary() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(outbox).publish(any());
        assertThrows(CommonException.class, () -> transactions.write("tenant", () -> {
            publish();
            return true;
        }));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Real database rollback acceptance is still required; this checks the transaction boundary only.
    }

    @Test
    void missingOrReadOnlyTransactionCannotPublish() {
        assertSame(INVALID_STATE, assertThrows(CommonException.class, this::publish).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(CommonException.class, this::publish);
        verifyNoInteractions(headers, outbox);
    }

    @Test
    void anotherTenantCannotReadOrPublishTheHeader() {
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class,
                () -> transactions.write("other", () -> {
                    publish();
                    return true;
                })).getResultCode());
        verifyNoInteractions(headers, outbox);
    }
}
