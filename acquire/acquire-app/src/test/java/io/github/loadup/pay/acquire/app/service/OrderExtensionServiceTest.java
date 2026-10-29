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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.OrderExtensionConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.client.command.AcquireOrderExtendCommand;
import io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderExtensionGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderExtensionReceipt;
import io.github.loadup.pay.acquire.domain.model.PaymentInteractionKind;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.OWNER;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.order;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.terms;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderExtensionServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AcquireOrderConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final OrderExtensionGateway store = mock(OrderExtensionGateway.class);
    final OrderCreationCodec codec = mock(OrderCreationCodec.class);
    final OrderSnapshotEvents snapshots = mock(OrderSnapshotEvents.class);
    final OrderDeadlineTasks deadlines = mock(OrderDeadlineTasks.class);
    final AtomicReference<StoredAcquireOrder> current = new AtomicReference<>(order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)));
    final AtomicReference<OrderExtensionReceipt> receipt = new AtomicReference<>();
    final AtomicLong deadlineVersion = new AtomicLong(1L);
    final UnitTransactions manager = new UnitTransactions();
    final OrderExtensionService service = new OrderExtensionService(
            () -> new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:extend")), orders, store, codec,
            context.getBean(AcquireOrderConverter.class), context.getBean(OrderExtensionConverter.class),
            new OrderCreationTransactions(manager), () -> "extend-receipt", validation.getValidator(), clock(NOW), snapshots, deadlines);
    final AcquireOrderExtendCommand command = new AcquireOrderExtendCommand("order", "extend-once", BusinessTime.external(NOW.plusMinutes(15)), 0L);
    final DirectPayRecoverCommand direct = new DirectPayRecoverCommand("merchant", "M001", "scope", 10000L, "CNY", "Original order",
            BusinessTime.external(NOW.plusMinutes(10)), "first", 1000L, new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), PaymentInteractionKind.H5_SCENE);

    @BeforeEach
    void setup() {
        when(orders.lockOrder("tenant", "order")).thenAnswer(c -> current.get());
        when(orders.findOrder("tenant", "order")).thenAnswer(c -> Optional.of(current.get()));
        when(store.deadlineVersion(eq("tenant"), any())).thenAnswer(c -> deadlineVersion.get());
        when(store.findReceipt("tenant", "order", "extend-once")).thenAnswer(c -> Optional.ofNullable(receipt.get()));
        when(codec.checksum(anyString())).thenAnswer(c -> DigestUtils.sha256((String) c.getArgument(0)));
        when(orders.findIntent("tenant", "merchant", "M001")).thenReturn(Optional.of(new OrderCreateIntent("create", "order", "merchant", "M001",
                "c".repeat(64), OWNER, "DIRECT_PAY", null, "metadata", "COMPLETED", "admission", NOW.plusMinutes(5), "SUCCESS", 1L)));
        when(codec.decodeDirectPay(any())).thenReturn(direct);
        when(codec.directPayDigest("tenant", OWNER, direct)).thenReturn("c".repeat(64));
        var t = terms();
        var extendedTerms = new AcquiringTermsDTO(t.schemaVersion(), new OrderTermsDTO(600L, 3600L,
                io.github.loadup.pay.product.domain.model.UnpaidOrderAction.CLOSE, true, 1200L, 300L),
                t.payment(), t.fee(), t.refund(), t.notification(), t.limits(), t.capabilities(), t.routingProfileRef(), t.returnTargetCode());
        when(codec.decodeAdmission(anyString(), anyString())).thenReturn(new OrderAdmissionDTO("admission", "order", "merchant",
                io.github.loadup.pay.product.domain.model.AdmissionDecision.ADMITTED, "ALLOW", "c".repeat(64), "contract", 1, 1L,
                "snapshot", "b".repeat(64), BusinessTime.external(NOW.minusMinutes(1)), BusinessTime.external(NOW.plusMinutes(5)), extendedTerms));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            AcquireOrder changed = c.getArgument(1);
            var old = current.get();
            current.set(new StoredAcquireOrder(changed, old.merchantOrderNo(), old.subject(), old.admission(), old.admittedAt(), old.callerScopeHash(),
                    old.snapshotId(), old.admissionPayload(), old.payloadHash()));
            receipt.set(c.getArgument(2));
            deadlineVersion.set(receipt.get().deadlineVersion());
            return null;
        }).when(store).extend(eq("tenant"), any(), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(current.get().order(), c.getArgument(1));
            assertEquals(2L, (long) c.getArgument(2));
            return null;
        }).when(deadlines).register(eq("tenant"), any(), anyLong());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void directPayUsesItsOriginalCreationMetadataAndSchedulesOnlyTheAppliedDeadline() {
        service.extend(command);
        service.extend(command);
        assertEquals(NOW.plusMinutes(15), current.get().order().orderDueAt());
        assertEquals(2L, deadlineVersion.get());
        verify(deadlines, times(1)).register("tenant", current.get().order(), 2L);
        verify(store, times(1)).extend(anyString(), any(), any());
        verify(codec).decodeDirectPay(any());
        verify(codec, never()).decodeIntent(any());
        assertEquals(2, manager.commits);
    }

    @Test
    void deadlineTaskRegistrationFailureRollsBackBeforePublishingTheNewDeadline() {
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        doThrow(failure).when(deadlines).register(anyString(), any(), anyLong());
        assertSame(failure, assertThrows(CommonException.class, () -> service.extend(command)));
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
        verifyNoInteractions(snapshots);
    }
}
