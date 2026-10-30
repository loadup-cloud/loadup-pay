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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.RefundSettlementConverter;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.facade.RefundSettlementFactsFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.payment.domain.model.RefundState;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;


import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class RefundSettlementFactsService implements RefundSettlementFactsFacade {
    private final PaymentActorProvider actors;
    private final PaymentAcceptanceTransactions transactions;
    private final RefundExecutionLocks locks;
    private final RefundResultGateway results;
    private final PaymentAcceptanceGateway payments;
    private final RefundSettlementConverter converter;
    private final Validator validator;
    private final String limitService;

    public RefundSettlementFactsService(PaymentActorProvider actors, PaymentAcceptanceTransactions transactions,
                                        RefundExecutionLocks locks, RefundResultGateway results, PaymentAcceptanceGateway payments,
                                        RefundSettlementConverter converter, Validator validator,
                                        @Value("${loadup.modules.payment.limitcenter-service-id:limitcenter}") String limitService) {
        this.actors = actors;
        this.transactions = transactions;
        this.locks = locks;
        this.results = results;
        this.payments = payments;
        this.converter = converter;
        this.validator = validator;
        this.limitService = DomainChecks.id(limitService);
    }

    @Override
    public RefundSettlementFactsDTO refundFacts(IdQuery query) {
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!limitService.equals(actor.serviceId()) || !actor.permissions().contains("pay:payment:refund-facts"))
            throw new CommonException(ACTION_FORBIDDEN);
        return transactions.writeLocal(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var requestId = results.requestId(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var locked = locks.lock(actor.tenantId(), requestId, query.id());
            var facts = locked.child();
            var child = facts.child();
            var refund = child.refund();
            if (refund.state() != RefundState.SUCCEEDED) throw new CommonException(INVALID_STATE);
            var claim = results.claim(actor.tenantId(), refund.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var global = results.transaction(claim.original().channel(), claim.original().accountScopeHash(), claim.channelRefundTransactionNo())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!actor.tenantId().equals(claim.tenantId()) || !refund.id().equals(claim.refundId())
                    || !child.original().equals(claim.original()) || !child.channelTransactionNo().equals(claim.originalChannelTransactionNo())
                    || !refund.channelRefundNo().equals(claim.channelRefundNo()) || !refund.amount().equals(claim.amount())
                    || !refund.terminalEvidenceId().equals(claim.receiptId())
                    || !Objects.equals(facts.channelRefundTransactionNo(), claim.channelRefundTransactionNo())
                    || !Objects.equals(facts.succeededAt(), claim.succeededAt())
                    || !claim.equals(global) || locked.capacity().refundedMinor() < refund.amount().getCent())
                throw new CommonException(SNAPSHOT_INVALID);
            var original = payments.find(actor.tenantId(), refund.paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            // RefundExecutionLocks has already verified the original successful attempt and permanent collection claim.
            if (original.terminalEvidenceId() == null) throw new CommonException(SNAPSHOT_INVALID);
            return converter.toFacts(converter.toOutcome(refund, locked.request().request(), original.payment(), locked.capacity()),
                    original, locked.capacity());
        });
    }
}
