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
import io.github.loadup.pay.payment.app.converter.RefundProjectionConverter;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.facade.RefundSummaryFactsFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundSummaryGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class RefundSummaryFactsService implements RefundSummaryFactsFacade {
    private final PaymentActorProvider actors;
    private final PaymentAcceptanceGateway payments;
    private final RefundSummaryGateway summaries;
    private final RefundProjectionConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final Validator validator;
    private final String acquireService;

    public RefundSummaryFactsService(PaymentActorProvider actors, PaymentAcceptanceGateway payments, RefundSummaryGateway summaries,
                                     RefundProjectionConverter converter, PaymentAcceptanceTransactions transactions, Validator validator,
                                     @Value("${loadup.modules.payment.acquire-service-id:acquire}") String acquireService) {
        this.actors = actors;
        this.payments = payments;
        this.summaries = summaries;
        this.converter = converter;
        this.transactions = transactions;
        this.validator = validator;
        this.acquireService = DomainChecks.id(acquireService);
    }

    @Override
    public OrderRefundSummaryEventDTO refundSummary(IdQuery query) {
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!acquireService.equals(actor.serviceId()) || !actor.permissions().contains("pay:payment:refund-summary:read"))
            throw new CommonException(ACTION_FORBIDDEN);
        return transactions.writeLocal(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var parent = payments.lockParent(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            if (!actor.serviceId().equals(parent.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
            if (!query.id().equals(parent.gate().orderId()) || parent.gate().paymentId() != null || parent.gate().state() == GateState.INIT)
                throw new CommonException(SNAPSHOT_INVALID);
            var value = summaries.lock(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            if (!value.orderId().equals(query.id()) || !value.payeeMerchantId().equals(parent.payeeMerchantId()))
                throw new CommonException(SNAPSHOT_INVALID);
            return converter.toEvent(value);
        });
    }
}
