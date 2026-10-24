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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.app.converter.RefundExecutionConverter;
import io.github.loadup.pay.payment.app.converter.RefundPlanningConverter;
import io.github.loadup.pay.payment.app.model.LockedRefund;
import io.github.loadup.pay.payment.app.model.RefundResultApplication;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundExecutionServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundExecutionConverter.class.getPackageName());
    final RefundExecutionConverter converter = context.getBean(RefundExecutionConverter.class);
    final RefundPlanningConverter views = context.getBean(RefundPlanningConverter.class);
    final ObjectProvider<PaymentChannelGateway> providers = mock(ObjectProvider.class);
    final PaymentChannelGateway channel = mock(PaymentChannelGateway.class);
    final RefundExecutionLocks locks = mock(RefundExecutionLocks.class);
    final RefundResultGateway changes = mock(RefundResultGateway.class);
    final RefundResultService results = mock(RefundResultService.class);
    final RefundProjectionEvents projections = mock(RefundProjectionEvents.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final AtomicReference<StoredRefundRequest> request = new AtomicReference<>(RefundResultFixtures.initial());
    final AtomicReference<RefundExecutionFacts> facts = new AtomicReference<>(new RefundExecutionFacts(RefundResultFixtures.initial().allocations().getFirst(), null, null, null));
    final RefundExecutionService service = new RefundExecutionService(providers, locks, changes, results, converter,
            new PaymentAcceptanceTransactions(manager), Clock.fixed(RefundResultFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()), projections);

    @BeforeEach
    void setup() {
        when(providers.getIfAvailable()).thenReturn(channel);
        when(locks.request("tenant", "request")).thenAnswer(c -> request.get());
        when(locks.child(eq("tenant"), any(), eq("refund"))).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return new LockedRefund(request.get(), facts.get(), RefundResultFixtures.capacity());
        });
        when(channel.canRefund(any())).thenReturn(true);
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            RefundProgressChange change = c.getArgument(1);
            var child = converter.withRefund(facts.get().child(), change.refund());
            request.set(converter.withRequest(request.get(), change.request(), List.of(child, request.get().allocations().get(1))));
            facts.set(new RefundExecutionFacts(child, change.submissionStartedAt(), null, null));
            return null;
        }).when(changes).update(eq("tenant"), any());
        when(channel.submitRefund(any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(manager.commits > 0);
            assertEquals(RefundState.PROCESSING, facts.get().child().refund().state());
            return RefundResultFixtures.observation(ChannelRefundOutcome.Kind.ACCEPTED, "submit", RefundObservation.Source.SUBMIT_RESPONSE);
        });
        when(channel.recoverRefund(any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return RefundResultFixtures.observation(ChannelRefundOutcome.Kind.UNKNOWN, "recover", RefundObservation.Source.QUERY_RESPONSE);
        });
        when(results.record(eq("tenant"), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new RefundResultApplication(RefundReceiptDisposition.IGNORED, "receipt",
                    views.toDTO(request.get().request(), views.toAllocations(request.get().allocations())));
        });
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void onlyTheInitWinnerSubmitsAndEveryReplayRecoversTheSameOriginalRequest() {
        service.progress("tenant", "request");
        service.progress("tenant", "request");
        verify(channel, times(1)).submitRefund(argThat(p -> p.refundId().equals("refund") && p.channelRefundNo().equals("refund-request")
                && p.originalChannelTransactionNo().equals("original-transaction") && p.originalAmount().getCent() == 4000));
        verify(channel, times(1)).recoverRefund(argThat(p -> p.refundId().equals("refund")
                && p.channelRefundNo().equals("refund-request")
                && p.originalChannelTransactionNo().equals("original-transaction") && p.originalAmount().getCent() == 4000));
        verify(changes, times(1)).update(anyString(), any());
        verify(projections, times(1)).changed(eq("tenant"), any());
        assertEquals(1000, RefundResultFixtures.capacity().reservedMinor());
    }

    @Test
    void transportFailurePreservesTheBoundaryAndDoesNotPermitAnotherSubmission() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(channel).submitRefund(any());
        assertThrows(CommonException.class, () -> service.progress("tenant", "request"));
        verify(results).uncertain("tenant", "request", "refund");
        service.progress("tenant", "request");
        verify(channel, times(1)).submitRefund(any());
        verify(channel, times(1)).recoverRefund(any());
    }

    @Test
    void recoveryAcceptsOnlyOriginalIdAcceptedOrUnknownSubmitResponsesOutsideTheTransaction() {
        service.progress("tenant", "request");
        when(channel.recoverRefund(any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return RefundResultFixtures.observation(ChannelRefundOutcome.Kind.ACCEPTED, "refund-request",
                    RefundObservation.Source.SUBMIT_RESPONSE);
        });
        service.progress("tenant", "request");
        when(channel.recoverRefund(any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return RefundResultFixtures.observation(ChannelRefundOutcome.Kind.UNKNOWN, "refund-request",
                    RefundObservation.Source.SUBMIT_RESPONSE);
        });
        service.progress("tenant", "request");
        verify(channel, times(2)).recoverRefund(argThat(p -> p.refundId().equals("refund")
                && p.channelRefundNo().equals("refund-request")));
        verify(channel, times(1)).submitRefund(any());
        verify(results, times(3)).record(eq("tenant"), any());
        verify(changes, times(1)).update(anyString(), any());
    }

    @Test
    void recoveryRejectsCallbackAndTerminalSubmitResponsesWithoutRecordingThem() {
        service.progress("tenant", "request");
        org.mockito.Mockito.clearInvocations(results);
        when(channel.recoverRefund(any())).thenReturn(RefundResultFixtures.observation(ChannelRefundOutcome.Kind.ACCEPTED,
                "refund-request", RefundObservation.Source.CALLBACK));
        assertThrows(CommonException.class, () -> service.progress("tenant", "request"));
        verify(results).uncertain("tenant", "request", "refund");
        verify(results, never()).record(eq("tenant"), any());

        when(channel.recoverRefund(any())).thenReturn(RefundResultFixtures.observation(ChannelRefundOutcome.Kind.SUCCEEDED,
                "refund-request", RefundObservation.Source.SUBMIT_RESPONSE));
        assertThrows(CommonException.class, () -> service.progress("tenant", "request"));
        when(channel.recoverRefund(any())).thenReturn(RefundResultFixtures.observation(ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND,
                "refund-request", RefundObservation.Source.SUBMIT_RESPONSE));
        assertThrows(CommonException.class, () -> service.progress("tenant", "request"));
        verify(results, times(3)).uncertain("tenant", "request", "refund");
        verify(results, never()).record(eq("tenant"), any());
        verify(channel, times(1)).submitRefund(any());
    }

    @Test
    void missingProviderOrUnsupportedLocalCapabilityCannotCrossTheSubmissionBoundary() {
        when(providers.getIfAvailable()).thenReturn(null);
        assertSame(UNSUPPORTED_CAPABILITY, assertThrows(CommonException.class, () -> service.progress("tenant", "request")).getResultCode());
        verifyNoInteractions(locks, changes);
        when(providers.getIfAvailable()).thenReturn(channel);
        when(channel.canRefund(any())).thenReturn(false);
        assertThrows(CommonException.class, () -> service.progress("tenant", "request"));
        assertNull(facts.get().submissionStartedAt());
        verify(changes, never()).update(anyString(), any());
        verify(channel, never()).submitRefund(any());
    }

    @Test
    void failedSubmissionCasNeverMakesTheNetworkCall() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(changes).update(anyString(), any());
        assertThrows(CommonException.class, () -> service.progress("tenant", "request"));
        verify(channel, never()).submitRefund(any());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void outerTransactionCannotContainTheChannelCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.progress("tenant", "request")).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(providers, locks, changes);
    }
}
