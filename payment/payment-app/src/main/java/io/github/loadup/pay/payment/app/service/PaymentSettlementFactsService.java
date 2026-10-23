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
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.facade.PaymentSettlementFactsFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class PaymentSettlementFactsService implements PaymentSettlementFactsFacade {
    private final PaymentActorProvider actors;
    private final PaymentAcceptanceGateway store;
    private final PaymentAcceptanceCodec codec;
    private final PaymentAcceptanceConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final Validator validator;
    private final String limitService;
    private final String acquireService;

    public PaymentSettlementFactsService(PaymentActorProvider actors, PaymentAcceptanceGateway store,
                                         PaymentAcceptanceCodec codec, PaymentAcceptanceConverter converter, PaymentAcceptanceTransactions transactions,
                                         Validator validator, @Value("${loadup.modules.payment.limitcenter-service-id:limitcenter}") String limitService,
                                         @Value("${loadup.modules.payment.acquire-service-id:acquire}") String acquireService) {
        this.actors = actors;
        this.store = store;
        this.codec = codec;
        this.converter = converter;
        this.transactions = transactions;
        this.validator = validator;
        this.limitService = limitService;
        DomainChecks.id(limitService);
        this.acquireService = DomainChecks.id(acquireService);
    }

    @Override
    public PaymentSettlementFactsDTO paymentFacts(IdQuery query) {
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        boolean limitAccess = limitService.equals(actor.serviceId()) && actor.permissions().contains("pay:payment:limit-facts");
        boolean acquireAccess = acquireService.equals(actor.serviceId()) && actor.permissions().contains("pay:payment:acquire-facts");
        if (!limitAccess && !acquireAccess)
            throw new CommonException(ACTION_FORBIDDEN);
        return transactions.write(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var first = store.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            if (acquireAccess && !actor.serviceId().equals(first.callerServiceId()))
                throw new CommonException(ACTION_FORBIDDEN);
            var parent = store.lockParent(actor.tenantId(), first.payment().orderId())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var gate = store.lockPayment(actor.tenantId(), query.id())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var current = store.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            codec.decode(current);
            if (acquireAccess && !actor.serviceId().equals(current.callerServiceId()))
                throw new CommonException(ACTION_FORBIDDEN);
            check(parent, current, null);
            check(gate, current, query.id());
            return converter.toSettlementFacts(current, converter.toDTO(current), gate.gate().state() == GateState.FENCED);
        });
    }

    private void check(StoredPaymentGate gate, StoredPaymentAcceptance current, String paymentId) {
        if (!gate.gate().orderId().equals(current.payment().orderId()) || !Objects.equals(gate.gate().paymentId(), paymentId)
                || !gate.payeeMerchantId().equals(current.payment().payeeMerchantId())
                || !gate.callerServiceId().equals(current.callerServiceId()) || gate.gate().state() == GateState.INIT)
            throw new CommonException(SNAPSHOT_INVALID);
    }
}
