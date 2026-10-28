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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.modules.contract.domain.model.Condition;
import io.github.loadup.modules.contract.domain.model.ConfigurationLayer;
import io.github.loadup.modules.contract.domain.model.ContractRecord;
import io.github.loadup.modules.contract.domain.model.ContractStatus;
import io.github.loadup.modules.contract.domain.model.MerchantContractRevision;
import io.github.loadup.modules.contract.domain.model.ResolvedContractItem;
import io.github.loadup.modules.contract.domain.model.ValueOrigin;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.product.app.converter.PolicyPublicationConverter;
import io.github.loadup.pay.product.app.converter.PolicyPublicationConverterImpl;
import io.github.loadup.pay.product.app.model.PolicyActivationDispatch;
import io.github.loadup.pay.product.app.port.CurrentContractGateway;
import io.github.loadup.pay.product.app.port.PolicyActivationClient;
import io.github.loadup.pay.product.app.port.PolicyPublicationCodec;
import io.github.loadup.pay.product.app.security.PublicationActor;
import io.github.loadup.pay.product.client.command.LimitPolicyPublishCommand;
import io.github.loadup.pay.product.client.command.LimitPolicyReplayCommand;
import io.github.loadup.pay.product.domain.gateway.PolicyPublicationGateway;
import io.github.loadup.pay.product.domain.model.PolicyPublication;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyPublicationServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PolicyPublicationConverterImpl.class);
    final CurrentContractGateway contracts = mock(CurrentContractGateway.class);
    final PolicyPublicationGateway publications = mock(PolicyPublicationGateway.class);
    final PolicyPublicationCodec codec = mock(PolicyPublicationCodec.class);
    final PolicyActivationClient client = mock(PolicyActivationClient.class);
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final UnitTransactions manager = new UnitTransactions();
    final LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0);
    final Clock clock = Clock.fixed(now.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final LimitPolicyCompiler compiler = new LimitPolicyCompiler(new AcquiringTermsCompiler(validation.getValidator()));
    final PublicationActor actor = new PublicationActor(
            "tenant",
            "operator",
            Set.of("pay:product:publish", "pay:product:publication:read", "pay:product:publication:replay"));
    final PolicyPublicationService service = new PolicyPublicationService(
            () -> actor,
            validation.getValidator(),
            new PublicationTransactions(manager),
            contracts,
            publications,
            compiler,
            codec,
            client,
            outbox,
            context.getBean(PolicyPublicationConverter.class),
            (IdGenerator) () -> "publication",
            clock);
    final MerchantContractRevision snapshot;
    final io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand activation;

    PolicyPublicationServiceTest() {
        var item = new ResolvedContractItem(
                "acquiring",
                1,
                "ACQUIRE",
                AcquiringFixtures.configuration(AcquiringFixtures.values()),
                Condition.always(),
                new ValueOrigin(ConfigurationLayer.BUNDLE, "bundle", 1));
        snapshot = new MerchantContractRevision(
                "tenant",
                "merchant",
                "scope",
                "contract",
                1,
                "plan",
                1,
                clock.instant().minusSeconds(3600),
                clock.instant().minusSeconds(3600),
                null,
                Map.of("acquiring", item));
        activation = compiler.compile("tenant", "merchant", item.configuration());
        when(client.supportsTenant("tenant")).thenReturn(true);
        when(codec.encode(eq("tenant"), any())).thenReturn("frozen-activation");
        when(codec.decode(eq("tenant"), any())).thenReturn(activation);
        when(outbox.publish(any())).thenReturn("event");
        queue("PENDING");
        when(contracts.lockByScope("tenant", "merchant", "scope"))
                .thenReturn(Optional.of(new ContractRecord(
                        "contract",
                        "tenant",
                        "merchant",
                        "scope",
                        "plan",
                        ContractStatus.NORMAL,
                        1,
                        "sign",
                        "digest",
                        "admin",
                        now,
                        now)));
        when(contracts.snapshot(any())).thenReturn(snapshot);
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    LimitPolicyPublishCommand command() {
        return new LimitPolicyPublishCommand("merchant", "scope", "contract", 1, snapshot.snapshotHash());
    }

    PolicyPublication pending() {
        return new PolicyPublication(
                "publication",
                "contract",
                1,
                snapshot.snapshotHash(),
                "merchant",
                "scope",
                "policy",
                1,
                activation.policyHash(),
                "frozen-activation",
                "INIT",
                null,
                now,
                null,
                1,
                0)
                .publishing("event");
    }

    void queue(String status) {
        when(outbox.find("tenant", "event"))
                .thenReturn(Optional.of(new OutboxRecord(
                        "event", "tenant", PolicyActivationDispatch.TYPE, "publication", status, 1, null)));
    }

    LimitPolicyActivationDTO ack() {
        return new LimitPolicyActivationDTO(
                "policy",
                1,
                activation.policyHash(),
                now.atZone(BusinessTime.zone()).toOffsetDateTime());
    }

    @Test
    void publicationAndOutboxEnqueueShareOneTransactionWithoutRemoteIo() {
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        })
                .when(publications)
                .insert(anyString(), any());
        when(outbox.publish(any())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return "event";
        });
        assertEquals("PUBLISHING", service.publish(command()).state());
        verify(client, never()).activate(anyString(), any());
        assertEquals(1, manager.commits);
    }

    @Test
    void originalPublicationReplaysWithoutCurrentContractRevalidationOrNewEvent() {
        when(publications.findByContract("tenant", "contract", 1)).thenReturn(Optional.of(pending()));
        assertEquals("event", service.publish(command()).outboxEventId());
        verifyNoInteractions(contracts);
        verify(outbox, never()).publish(any());
    }

    @Test
    void queueInsertFailureRollsBackThePublicationIntent() {
        when(outbox.publish(any()))
                .thenThrow(new CommonException(
                        io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE));
        assertThrows(CommonException.class, () -> service.publish(command()));
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
    }

    @Test
    void theRemoteActivationCallRunsOutsideTheAckTransaction() {
        when(publications.findById("tenant", "publication")).thenReturn(Optional.of(pending()));
        when(publications.lockById("tenant", "publication")).thenReturn(Optional.of(pending()));
        when(client.activate(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return ack();
        });
        service.deliver("tenant", "event", "publication");
        verify(publications)
                .update(
                        eq("tenant"),
                        argThat(value -> "ACTIVE".equals(value.state())
                                && value.activatedAt().equals(now)),
                        eq(1L));
    }

    @Test
    void wrongHashAckDoesNotActivateTheLocalContractReceipt() {
        when(publications.findById("tenant", "publication")).thenReturn(Optional.of(pending()));
        when(client.activate(anyString(), any()))
                .thenReturn(new LimitPolicyActivationDTO("policy", 1, "f".repeat(64), ack().activatedAt()));
        assertThrows(CommonException.class, () -> service.deliver("tenant", "event", "publication"));
        verify(publications, never()).lockById(anyString(), anyString());
    }

    @Test
    void remoteSuccessBeforeALocalFailureIsSafeToRetryWithTheSamePolicy() {
        when(publications.findById("tenant", "publication")).thenReturn(Optional.of(pending()));
        when(publications.lockById("tenant", "publication")).thenReturn(Optional.of(pending()));
        when(client.activate(anyString(), any())).thenReturn(ack());
        doThrow(new CommonException(
                io.github.loadup.pay.product.client.error.ProductResultCodeEnum.POLICY_PUBLICATION_CONFLICT))
                .doNothing()
                .when(publications)
                .update(eq("tenant"), any(), eq(1L));
        assertThrows(CommonException.class, () -> service.deliver("tenant", "event", "publication"));
        service.deliver("tenant", "event", "publication");
        verify(client, times(2)).activate("tenant", activation);
    }

    @Test
    void anAlreadyActiveReceiptCompletesDuplicateDeliveryWithoutAnotherNetworkCall() {
        when(publications.findById("tenant", "publication"))
                .thenReturn(Optional.of(pending().acknowledge("event", now)));
        service.deliver("tenant", "event", "publication");
        verify(client, never()).activate(anyString(), any());
    }

    @Test
    void aCrossWiredOutboxEventCannotActivateThePublication() {
        when(publications.findById("tenant", "publication")).thenReturn(Optional.of(pending()));
        assertThrows(CommonException.class, () -> service.deliver("tenant", "other-event", "publication"));
        verify(client, never()).activate(anyString(), any());
    }

    @Test
    void completionBetweenDetailReadsReloadsTheAckInsteadOfReportingCorruption() {
        when(publications.findById("tenant", "publication"))
                .thenReturn(Optional.of(pending()), Optional.of(pending().acknowledge("event", now)));
        queue("SUCCEEDED");
        assertEquals("ACTIVE", service.detail(new IdQuery("publication")).state());
    }

    @Test
    void failedDeliveryReplayUsesTheOriginalEventAndAuditsTheOperator() {
        when(publications.findById("tenant", "publication")).thenReturn(Optional.of(pending()));
        queue("FAILED");
        when(outbox.replay("tenant", "event", "operator", "reviewed")).thenReturn(true);
        service.replay(new LimitPolicyReplayCommand("publication", "reviewed"));
        verify(outbox).replay("tenant", "event", "operator", "reviewed");
        verify(outbox, never()).publish(any());
    }

    static final class UnitTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
