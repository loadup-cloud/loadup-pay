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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.modules.contract.domain.model.Condition;
import io.github.loadup.modules.contract.domain.model.ConfigurationLayer;
import io.github.loadup.modules.contract.domain.model.ContractRecord;
import io.github.loadup.modules.contract.domain.model.ContractStatus;
import io.github.loadup.modules.contract.domain.model.MerchantContractRevision;
import io.github.loadup.modules.contract.domain.model.ResolvedContractItem;
import io.github.loadup.modules.contract.domain.model.ValueOrigin;
import io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.common.ContractReference;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.product.app.converter.OrderAdmissionConverter;
import io.github.loadup.pay.product.app.converter.OrderAdmissionConverterImpl;
import io.github.loadup.pay.product.app.model.ActivatedLimitPolicy;
import io.github.loadup.pay.product.app.model.CompiledAcquiringTerms;
import io.github.loadup.pay.product.app.model.EncodedAcquiringTerms;
import io.github.loadup.pay.product.app.port.CompiledTermsCodec;
import io.github.loadup.pay.product.app.port.CurrentContractGateway;
import io.github.loadup.pay.product.app.port.CustomerEligibilityLookup;
import io.github.loadup.pay.product.app.security.ProductActor;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import io.github.loadup.pay.product.domain.gateway.OrderAdmissionGateway;
import io.github.loadup.pay.product.domain.model.AdmissionDecision;
import io.github.loadup.pay.product.domain.model.AdmissionIntent;
import io.github.loadup.pay.product.domain.model.OrderAdmission;
import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderAdmissionServiceTest {
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(OrderAdmissionConverterImpl.class);
    private final OrderAdmissionGateway admissions = mock(OrderAdmissionGateway.class);
    private final CurrentContractGateway contracts = mock(CurrentContractGateway.class);
    private final CustomerEligibilityLookup customer = mock(CustomerEligibilityLookup.class);
    private final CompiledTermsCodec codec = mock(CompiledTermsCodec.class);
    private final ProductActor actor = new ProductActor(
            "tenant", "MERCHANT_APP", "app", "merchant", "synthetic", Set.of("pay:product:admit", "pay:product:read"));
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0);
    private final UnitTransactions transactions = new UnitTransactions();
    private final AcquiringTermsCompiler compiler = new AcquiringTermsCompiler(validation.getValidator());
    private final OrderAdmissionService service = new OrderAdmissionService(
            () -> actor,
            admissions,
            contracts,
            customer,
            compiler,
            codec,
            context.getBean(OrderAdmissionConverter.class),
            validation.getValidator(),
            (IdGenerator) () -> "admission",
            Clock.fixed(now.atZone(BusinessTime.ZONE).toInstant(), BusinessTime.ZONE),
            300,
            transactions);

    OrderAdmissionServiceTest() {
        when(customer.authorizeNewOrder("tenant", "merchant")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new MerchantEligibilityDTO(
                    true,
                    "ALLOWED",
                    new MerchantProfileDTO(
                            "merchant", "tenant", "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true),
                    2,
                    "grant",
                    1L);
        });
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    private OrderAdmissionAuthorizeCommand command(long amount) {
        return new OrderAdmissionAuthorizeCommand("order", "merchant", "scope", "a".repeat(64), amount, "CNY", null);
    }

    private AdmissionIntent intent(long amount) {
        return new AdmissionIntent(
                "order",
                "merchant",
                "scope",
                "a".repeat(64),
                DomainChecks.amount(amount, "CNY"),
                null,
                actor.scopeHash());
    }

    private StoredOrderAdmission denied(String reason) {
        return new StoredOrderAdmission(
                intent(100),
                new OrderAdmission(
                        "admission",
                        "order",
                        "merchant",
                        "a".repeat(64),
                        AdmissionDecision.DENIED,
                        reason,
                        null,
                        null,
                        null),
                2,
                1,
                null);
    }

    private void current(ContractStatus status) {
        when(contracts.lockByScope("tenant", "merchant", "scope")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(new ContractRecord(
                    "contract",
                    "tenant",
                    "merchant",
                    "scope",
                    "plan",
                    status,
                    1,
                    "sign",
                    "digest",
                    "admin",
                    now.minusDays(1),
                    now.minusDays(1)));
        });
        var item = new ResolvedContractItem(
                "acquiring",
                1,
                "ACQUIRE",
                AcquiringFixtures.configuration(AcquiringFixtures.values()),
                Condition.always(),
                new ValueOrigin(ConfigurationLayer.BUNDLE, "bundle", 1));
        when(contracts.snapshot(any()))
                .thenReturn(new MerchantContractRevision(
                        "tenant",
                        "merchant",
                        "scope",
                        "contract",
                        1,
                        "plan",
                        1,
                        now.minusDays(1).atZone(BusinessTime.ZONE).toInstant(),
                        now.minusDays(1).atZone(BusinessTime.ZONE).toInstant(),
                        null,
                        Map.of("acquiring", item)));
    }

    @Test
    void originalDenialRemainsStickyAfterCurrentQualificationCouldChange() {
        when(admissions.findByOrder("tenant", "order")).thenReturn(Optional.of(denied("POLICY_NOT_ACTIVE")));
        assertEquals("POLICY_NOT_ACTIVE", service.authorize(command(100)).reasonCode());
        verifyNoInteractions(customer, contracts);
    }

    @Test
    void admittedReplayDoesNotRecheckCurrentStatusOrRefreshTheRecoveryDeadline() {
        var policy = new ActivatedLimitPolicy("policy", 1, "b".repeat(64), "ACTIVE", now.minusDays(1));
        var configuration = AcquiringFixtures.configuration(AcquiringFixtures.values());
        var terms = compiler.compile(configuration, policy);
        var original = new OrderAdmission(
                "admission",
                "order",
                "merchant",
                "a".repeat(64),
                AdmissionDecision.ADMITTED,
                "ALLOW",
                new ContractReference("contract", 1, 1, "snapshot", "c".repeat(64)),
                now.minusDays(1),
                now.minusHours(23));
        when(admissions.findByOrder("tenant", "order"))
                .thenReturn(Optional.of(new StoredOrderAdmission(intent(100), original, 2, 1, "compiled")));
        when(codec.decode(any()))
                .thenReturn(new CompiledAcquiringTerms(1, "contract", 1, "snapshot", terms, configuration.origins()));
        assertEquals(
                now.minusHours(23),
                service.authorize(command(100)).materializeBefore().toLocalDateTime());
        verifyNoInteractions(customer, contracts);
    }

    @Test
    void changedAmountWithTheSameDigestConflictsBeforeRemoteLookup() {
        when(admissions.findByOrder("tenant", "order")).thenReturn(Optional.of(denied("POLICY_NOT_ACTIVE")));
        var error = assertThrows(CommonException.class, () -> service.authorize(command(200)));
        assertSame(ProductResultCodeEnum.IDEMPOTENCY_CONFLICT, error.getResultCode());
        verifyNoInteractions(customer, contracts);
    }

    @Test
    void dependencyFailureDoesNotPersistAFalseDecision() {
        when(customer.authorizeNewOrder("tenant", "merchant"))
                .thenThrow(new CommonException(ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE));
        assertThrows(CommonException.class, () -> service.authorize(command(100)));
        verifyNoInteractions(contracts);
        verify(admissions, never()).insert(anyString(), any());
    }

    @Test
    void stoppedContractDeniesWithoutLoadingOrRecompilingHistoricalTerms() {
        current(ContractStatus.SUSPENDED);
        var dto = service.authorize(command(100));
        assertEquals("CONTRACT_NOT_ADMISSIBLE", dto.reasonCode());
        assertNull(dto.terms());
        assertNull(dto.revision());
        assertNull(dto.generation());
        verify(contracts, never()).snapshot(any());
        verifyNoInteractions(codec);
    }

    @Test
    void activeContractWithoutTrustedLimitAcknowledgementDenies() {
        current(ContractStatus.NORMAL);
        assertEquals("POLICY_NOT_ACTIVE", service.authorize(command(100)).reasonCode());
        verifyNoInteractions(codec);
    }

    @Test
    void validAdmissionFreezesTermsAndUsesThePlatformRecoveryWindow() {
        current(ContractStatus.NORMAL);
        var policy = new ActivatedLimitPolicy("policy", 1, "b".repeat(64), "ACTIVE", now.minusHours(1));
        when(contracts.publication("tenant", "contract", 1)).thenReturn(Optional.of(policy));
        var snapshot = contracts.snapshot(new ContractRecord(
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
                now));
        var terms = compiler.compile(snapshot.items().get("acquiring").configuration(), policy);
        when(codec.encode(any())).thenReturn(new EncodedAcquiringTerms("c".repeat(64), "compiled-json"));
        when(codec.decode(any()))
                .thenReturn(new CompiledAcquiringTerms(
                        1,
                        "contract",
                        1,
                        snapshot.snapshotHash(),
                        terms,
                        snapshot.items().get("acquiring").configuration().origins()));
        var dto = service.authorize(command(100));
        assertEquals(io.github.loadup.pay.product.client.enums.AdmissionDecision.ADMITTED, dto.decision());
        assertEquals(now.plusMinutes(5), dto.materializeBefore().toLocalDateTime());
        assertEquals("routing-v1", dto.terms().routingProfileRef());
        var saved = ArgumentCaptor.forClass(StoredOrderAdmission.class);
        verify(admissions).insert(eq("tenant"), saved.capture());
        assertEquals("compiled-json", saved.getValue().compiledTerms());
    }

    @Test
    void lockWaitRechecksTheWinnerBeforeMakingAnotherDecision() {
        current(ContractStatus.NORMAL);
        when(admissions.findByOrder("tenant", "order"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(denied("ORIGINAL")));
        assertEquals("ORIGINAL", service.authorize(command(100)).reasonCode());
        verify(admissions, never()).insert(anyString(), any());
        verify(contracts, never()).snapshot(any());
    }

    @Test
    void duplicateInsertRollsBackBeforeReadingTheWinner() {
        AtomicInteger reads = new AtomicInteger();
        when(admissions.findByOrder("tenant", "order")).thenAnswer(call -> {
            if (reads.incrementAndGet() < 4) return Optional.empty();
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, transactions.rollbacks);
            return Optional.of(denied("WINNER"));
        });
        doThrow(new DuplicateKeyException("synthetic conflict"))
                .when(admissions)
                .insert(anyString(), any());
        assertEquals("WINNER", service.authorize(command(100)).reasonCode());
    }

    @Test
    void ambientTransactionCannotEncloseTheCustomerNetworkCall() {
        new TransactionTemplate(transactions).execute(status -> {
            var failure = assertThrows(CommonException.class, () -> service.authorize(command(100)));
            assertSame(ProductResultCodeEnum.INVALID_STATE, failure.getResultCode());
            return null;
        });
        verifyNoInteractions(customer, admissions, contracts);
    }

    private static final class UnitTransactions extends AbstractPlatformTransactionManager {
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
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
