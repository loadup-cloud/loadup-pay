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
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.app.converter.PaymentEventConverter;
import io.github.loadup.pay.acquire.app.port.PaymentEventCodec;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActor;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActorProvider;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.facade.PaymentOutcomeFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentEventReceiptGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.model.PaymentEventReceipt;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;


import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class PaymentOutcomeEventService implements PaymentOutcomeFacade {
    public static final String TYPE = "pay.payment.outcome.acquire.v1";
    private final AcquireServiceActorProvider actors;
    private final PaymentOutcomeClient payment;
    private final OrderCreationGateway orders;
    private final PaymentOutcomeGateway allocations;
    private final PaymentEventReceiptGateway receipts;
    private final PaymentOutcomeOperations outcomes;
    private final PaymentEventCodec codec;
    private final PaymentEventConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Clock clock;
    private final String paymentService;

    public PaymentOutcomeEventService(AcquireServiceActorProvider actors, PaymentOutcomeClient payment,
                                      OrderCreationGateway orders, PaymentOutcomeGateway allocations, PaymentEventReceiptGateway receipts,
                                      PaymentOutcomeOperations outcomes, PaymentEventCodec codec, PaymentEventConverter converter,
                                      OrderCreationTransactions transactions, IdGenerator ids, @Qualifier("acquireClock") Clock clock,
                                      @Value("${loadup.modules.acquire.payment-service-id:payment}") String paymentService) {
        this.actors = actors;
        this.payment = payment;
        this.orders = orders;
        this.allocations = allocations;
        this.receipts = receipts;
        this.outcomes = outcomes;
        this.codec = codec;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
        this.paymentService = DomainChecks.id(paymentService);
    }

    @Override
    public PaymentOutcomeAckDTO accept(EventEnvelope<PaymentOutcomeEventDTO> event) {
        var actor = actors.current();
        if (!paymentService.equals(actor.serviceId()) || !actor.permissions().contains("pay:acquire:payment:outcome"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        validate(event);
        if (!payment.supportsTenant(actor.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.withTenantContext(actor.tenantId(), () -> {
            String digest = codec.digest(actor.tenantId(), actor.serviceId(), event);
            var prior = receipts.find(actor.tenantId(), event.deliveryId());
            if (prior.isPresent()) return replay(actor, prior.get(), digest);
            var data = event.data();
            var order = orders.findOrder(actor.tenantId(), data.orderId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var allocation = allocations.findPayment(actor.tenantId(), data.orderId(), data.paymentId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            // Original ownership is restored from the stored order, never from an event-supplied merchant scope.
            var owner = new OrderPaymentScope(actor.tenantId(), order.callerScopeHash());
            var command = outcomes.bound(owner, order, allocation);
            if (!command.payeeMerchantId().equals(data.payeeMerchantId()) || !command.allocationId().equals(data.allocationId())
                    || command.amountMinor() != data.amountMinor() || !command.currency().equals(data.currency()))
                throw new CommonException(SNAPSHOT_INVALID);
            var facts = payment.facts(actor.tenantId(), data.paymentId()); // No local transaction or lock is held.
            if (facts == null || !data.operationId().equals(facts.limitOperationId())
                    || !data.payeeMerchantId().equals(facts.payeeMerchantId()))
                throw new CommonException(SNAPSHOT_INVALID);
            var observed = converter.toObserved(facts);
            var verified = outcomes.terminal(command, observed);
            if (verified == null || observed.payment().state() != data.state() || observed.payment().version() != data.paymentVersion()
                    || !observed.payment().terminalEvidenceId().equals(data.evidenceId())
                    || data.executionFenced() && !observed.executionFenced())
                throw new CommonException(SNAPSHOT_INVALID);
            return transactions.writeLocal(actor.tenantId(), () -> {
                var existing = receipts.find(actor.tenantId(), event.deliveryId());
                if (existing.isPresent()) return replay(actor, existing.get(), digest);
                var changed = outcomes.convergeLocked(owner, command, verified);
                var accepted = converter.toReceipt(ids.generate(), event, digest, actor.serviceId(), changed.order().version(),
                        LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone()));
                receipts.insert(actor.tenantId(), accepted);
                return converter.toAck(accepted);
            });
        });
    }

    private PaymentOutcomeAckDTO replay(AcquireServiceActor actor, PaymentEventReceipt receipt, String digest) {
        if (!receipt.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!receipt.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toAck(receipt);
    }

    private void validate(EventEnvelope<PaymentOutcomeEventDTO> event) {
        try {
            if (event == null || event.data() == null) throw new CommonException(INVALID_ARGUMENT);
            event.data().requireEnvelope(event);
            if (!TYPE.equals(event.eventType()) || !event.businessEventId().equals(event.data().evidenceId())
                    || !Set.of(PaymentState.SUCCEEDED, PaymentState.FAILED, PaymentState.CLOSED).contains(event.data().state()))
                throw new CommonException(INVALID_ARGUMENT);
        } catch (DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }
}
