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
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.UnknownRecoveryOperationsConverter;
import io.github.loadup.pay.payment.app.model.UnknownRecoveryEntry;
import io.github.loadup.pay.payment.app.model.UnknownRecoveryWindow;
import io.github.loadup.pay.payment.app.port.UnknownRecoveryReader;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.dto.PaymentUnknownRecoveryPageDTO;
import io.github.loadup.pay.payment.client.facade.PaymentUnknownRecoveryFacade;
import io.github.loadup.pay.payment.client.query.PaymentUnknownRecoveryQuery;
import io.github.loadup.pay.payment.domain.policy.RecoveryEscalationPolicy;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * No mutations, native dispatch or channel calls; retry still requires the separate locked audit path.
 */
@Service
public class PaymentUnknownRecoveryService implements PaymentUnknownRecoveryFacade {
    private final PaymentActorProvider actors;
    private final UnknownRecoveryReader reader;
    private final UnknownRecoveryOperationsConverter converter;
    private final RecoveryEscalationPolicy policy;
    private final PaymentAcceptanceTransactions transactions;
    private final Validator validator;
    private final Clock clock;
    private final String operationsService;

    public PaymentUnknownRecoveryService(PaymentActorProvider actors, UnknownRecoveryReader reader,
                                         UnknownRecoveryOperationsConverter converter, RecoveryEscalationPolicy policy,
                                         PaymentAcceptanceTransactions transactions, Validator validator, @Qualifier("paymentClock") Clock clock,
                                         @Value("${loadup.modules.payment.recovery-operations-service-id:pay-ops}") String operationsService) {
        this.actors = actors;
        this.reader = reader;
        this.converter = converter;
        this.policy = policy;
        this.transactions = transactions;
        this.validator = validator;
        this.clock = clock;
        this.operationsService = DomainChecks.id(operationsService);
    }

    @Override
    public PaymentUnknownRecoveryPageDTO list(PaymentUnknownRecoveryQuery query) {
        var actor = actors.current();
        if (!operationsService.equals(actor.serviceId()) || !actor.permissions().contains("pay:payment:recovery:read"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        final UnknownRecoveryWindow window;
        try {
            var after = query.afterFirstUnknownAt() == null ? null : BusinessTime.local(query.afterFirstUnknownAt());
            window = new UnknownRecoveryWindow(policy.cutoff(converter.toLevel(query.minimumLevel()), now), after, query.afterRecordId(), query.limit());
        } catch (DomainViolation | java.time.DateTimeException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        return transactions.readSnapshot(actor.tenantId(), () -> {
            var rows = reader.list(actor.tenantId(), query.kind(), window);
            requireWindow(rows, query, window);
            boolean more = rows.size() > query.limit();
            var visible = rows.subList(0, Math.min(rows.size(), query.limit()));
            var items = visible.stream().map(row -> converter.toDTO(row, policy.level(row.firstUnknownAt(), now),
                    policy.ageSeconds(row.firstUnknownAt(), now))).toList();
            var last = more ? visible.getLast() : null;
            return new PaymentUnknownRecoveryPageDTO(items, more, last == null ? null : BusinessTime.external(last.firstUnknownAt()),
                    last == null ? null : last.recordId(), BusinessTime.external(now), policy.reviewAfterSeconds(), policy.urgentAfterSeconds());
        });
    }

    private void requireWindow(List<UnknownRecoveryEntry> rows, PaymentUnknownRecoveryQuery query, UnknownRecoveryWindow window) {
        if (rows == null || rows.size() > query.limit() + 1) throw new CommonException(SNAPSHOT_INVALID);
        var previousAt = window.afterFirstUnknownAt();
        var previousId = window.afterRecordId();
        for (var row : rows) {
            if (row == null || row.kind() != query.kind() || window.cutoff() != null && row.firstUnknownAt().isAfter(window.cutoff())
                    || previousAt != null && (row.firstUnknownAt().isBefore(previousAt)
                    || row.firstUnknownAt().equals(previousAt) && Arrays.compareUnsigned(row.recordId().getBytes(StandardCharsets.UTF_8),
                    previousId.getBytes(StandardCharsets.UTF_8)) <= 0))
                throw new CommonException(SNAPSHOT_INVALID);
            previousAt = row.firstUnknownAt();
            previousId = row.recordId();
        }
    }
}
