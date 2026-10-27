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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.payment.app.converter.PaymentCaptureConverter;
import io.github.loadup.pay.payment.app.security.*;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.facade.PaymentCaptureFacade;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentCaptureGateway;
import io.github.loadup.pay.payment.domain.model.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

@Service
public class PaymentCaptureService implements PaymentCaptureFacade {
    private final PaymentActorProvider actors;
    private final PaymentCaptureGateway captures;
    private final PaymentAcceptanceTransactions transactions;
    private final DelayedTaskTemplate tasks;
    private final PaymentCaptureConverter converter;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final String reconciliationService;
    public PaymentCaptureService(PaymentActorProvider actors, PaymentCaptureGateway captures, PaymentAcceptanceTransactions transactions,
            DelayedTaskTemplate tasks, PaymentCaptureConverter converter, IdGenerator ids, Validator validator, @Qualifier("paymentClock") Clock clock,
            @Value("${loadup.modules.payment.reconciliation-service-id:reconciliation}") String reconciliationService) {
        this.actors = actors; this.captures = captures; this.transactions = transactions; this.tasks = tasks; this.converter = converter;
        this.ids = ids; this.validator = validator; this.clock = clock; this.reconciliationService = DomainChecks.id(reconciliationService);
    }
    @Override public PaymentCaptureDTO create(PaymentCaptureCreateCommand command) {
        validate(command); var actor = actor("create");
        var scope = transactions.withTenantContext(actor.tenantId(), () -> new PaymentCaptureScope(command.channel(), command.accountScopeHash(), command.billDate(), command.statementProfile(), clock.getZone().getId()));
        if (!command.billDate().isBefore(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalDate())) throw new CommonException(INVALID_ARGUMENT);
        String digest = digest(actor, command, scope.systemZone());
        return transactions.writeLocal(actor.tenantId(), () -> {
            var existing = captures.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey());
            if (existing.isPresent()) {
                var value = authorized(actor, existing.get()); if (!value.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
                return present(actor.tenantId(), value);
            }
            var value = PaymentCapture.init(ids.generate(), actor.serviceId(), command.requestKey(), digest, scope);
            captures.insert(actor.tenantId(), value);
            tasks.register(PaymentCaptureExecutionService.TYPE, value.id(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
            return present(actor.tenantId(), value);
        });
    }
    @Override public PaymentCaptureDTO retry(io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand command) {
        validate(command); var actor = actor("retry");
        return transactions.writeLocal(actor.tenantId(), () -> {
            var value = authorized(actor, captures.lock(actor.tenantId(), command.captureId()).orElseThrow(() -> new CommonException(NOT_FOUND)));
            var task = present(actor.tenantId(), value);
            if (!command.expectedTaskId().equals(task.nativeTaskId())) throw new CommonException(IDEMPOTENCY_CONFLICT);
            if ("CANCELLED".equals(task.nativeStatus())) throw new CommonException(INVALID_STATE);
            if ("FAILED".equals(task.nativeStatus())) {
                try { tasks.retryFailed(PaymentCaptureExecutionService.TYPE, value.id()); }
                catch (CommonException failure) { throw new CommonException(INVALID_STATE); }
            }
            return present(actor.tenantId(), value);
        });
    }
    @Override public PaymentCaptureDTO detail(IdQuery query) {
        validate(query); var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> present(actor.tenantId(), authorized(actor, captures.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND)))));
    }
    @Override public PageDTO<PaymentLedgerLineDTO> lines(PaymentCaptureLinesQuery query) {
        validate(query); var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> {
            var capture = authorized(actor, captures.find(actor.tenantId(), query.captureId()).orElseThrow(() -> new CommonException(NOT_FOUND)));
            if (capture.state() != PaymentCaptureState.SEALED) throw new CommonException(INVALID_STATE);
            var page = captures.page(actor.tenantId(), capture, query.page(), query.size());
            if (page.page() != query.page() || page.size() != query.size() || page.total() != Math.addExact(capture.paymentCount(), capture.refundCount())) throw new CommonException(SNAPSHOT_INVALID);
            return PageDTO.of(page.map(line -> converter.toDTO(line, capture.scope().systemZone())));
        });
    }
    private PaymentCaptureDTO present(String tenant, PaymentCapture value) {
        var task = tasks.find(PaymentCaptureExecutionService.TYPE, value.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!tenant.equals(task.tenantId()) || !value.id().equals(task.bizId()) || !PaymentCaptureExecutionService.TYPE.equals(task.taskType())) throw new CommonException(SNAPSHOT_INVALID);
        return converter.toDTO(value, task);
    }
    private PaymentCapture authorized(PaymentActor actor, PaymentCapture value) {
        if (!actor.serviceId().equals(value.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN); return value;
    }
    private PaymentActor actor(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor == null || !reconciliationService.equals(actor.serviceId()) || !actor.permissions().contains("pay:payment:reconciliation:capture:" + operation)) throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }
    private void validate(Object value) { if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT); }
    private String digest(PaymentActor actor, PaymentCaptureCreateCommand command, String zone) {
        var framed = new StringBuilder();
        for (var field : java.util.List.of("payment-capture-request-v1", actor.tenantId(), actor.serviceId(), command.requestKey(), command.channel().name(),
                command.accountScopeHash(), command.billDate().toString(), command.statementProfile(), zone)) framed.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        return DigestUtils.sha256(framed.toString());
    }
}
