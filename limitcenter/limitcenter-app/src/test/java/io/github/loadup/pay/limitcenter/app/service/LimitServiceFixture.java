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
package io.github.loadup.pay.limitcenter.app.service;

import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.limitcenter.app.converter.LimitConverter;
import io.github.loadup.pay.limitcenter.app.converter.LimitConverterImpl;
import io.github.loadup.pay.limitcenter.app.security.LimitActor;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO;
import io.github.loadup.pay.limitcenter.client.enums.LimitMetric;
import io.github.loadup.pay.limitcenter.client.enums.LimitWindowType;
import io.github.loadup.pay.limitcenter.client.enums.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.client.enums.RefundCountRestorePolicy;
import io.github.loadup.pay.limitcenter.client.support.LimitPolicyHash;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitPolicyGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.MerchantLimitGateway;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;

/**
 * Source-level unit fixture. Its transaction manager records boundaries; it cannot prove database rollback or locking.
 */
class LimitServiceFixture implements AutoCloseable {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LimitConverterImpl.class);
    final LimitConverter converter = context.getBean(LimitConverter.class);
    final LimitPolicyGateway policies = mock(LimitPolicyGateway.class);
    final MerchantLimitGateway reservations = mock(MerchantLimitGateway.class);
    final LimitActor actor = new LimitActor(
            "tenant",
            "payment",
            Set.of(
                    "pay:limit:activate",
                    "pay:limit:reserve",
                    "pay:limit:read",
                    "pay:limit:execute",
                    "pay:limit:verify",
                    "pay:limit:expire"));
    final LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0);
    final Clock clock = Clock.fixed(now.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final UnitTransactions manager = new UnitTransactions();
    final LimitTransactions transactions = new LimitTransactions(() -> actor, validation.getValidator(), manager);
    final AtomicInteger ids = new AtomicInteger();
    final IdGenerator generator = () -> "id-" + ids.incrementAndGet();
    final LimitPolicyService policyService = new LimitPolicyService(transactions, policies, converter, clock);
    final LimitReservationService reservationService =
            new LimitReservationService(transactions, policies, reservations, converter, generator, clock, 120);

    LimitPolicyActivateCommand activation(int version, long threshold) {
        var rule = new LimitRuleDTO(
                "stable", "merchant", LimitMetric.AMOUNT, "CNY", LimitWindowType.LIFETIME, 1, threshold, true, true);
        var unsigned = new LimitPolicyActivateCommand(
                "policy",
                version,
                "a".repeat(64),
                "merchant",
                RefundAmountRestorePolicy.ORIGINAL_BUCKETS,
                RefundCountRestorePolicy.NO_RESTORE,
                List.of(rule));
        return new LimitPolicyActivateCommand(
                unsigned.policyId(),
                version,
                LimitPolicyHash.compute("tenant", unsigned),
                unsigned.payeeMerchantId(),
                unsigned.refundAmountRestore(),
                unsigned.refundCountRestore(),
                unsigned.rules());
    }

    ActivatedLimitPolicy policy(int version, long threshold) {
        return converter.toPolicy(activation(version, threshold), now.minusHours(1));
    }

    LimitReserveCommand command(long amount) {
        return new LimitReserveCommand(
                "operation",
                "payment-id",
                "order",
                "merchant",
                amount,
                "CNY",
                now.minusDays(1).atZone(BusinessTime.zone()).toOffsetDateTime(),
                "policy",
                1,
                policy(1, 100).policyHash(),
                "b".repeat(64));
    }

    @Override
    public void close() {
        context.close();
        validation.close();
    }

    static final class UnitTransactions extends AbstractPlatformTransactionManager {
        int begins;
        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begins++;
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
