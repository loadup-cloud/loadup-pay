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
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.FeeRefundConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActor;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActorProvider;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.client.facade.FeeRefundOutcomeFacade;
import io.github.loadup.pay.acquire.domain.gateway.FeeRefundReceiptGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderFeeGateway;
import io.github.loadup.pay.acquire.domain.model.FeeRefundReceipt;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;


import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class FeeRefundOutcomeService implements FeeRefundOutcomeFacade {
    public static final String TYPE = "pay.payment.refund.fee.v1";
    private final AcquireServiceActorProvider actors;
    private final PaymentOutcomeClient payment;
    private final OrderCreationGateway orders;
    private final OrderFeeGateway fees;
    private final FeeRefundReceiptGateway receipts;
    private final OrderCreationCodec codec;
    private final AcquireOrderConverter policies;
    private final FeeRefundConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Clock clock;
    private final String paymentService;

    public FeeRefundOutcomeService(AcquireServiceActorProvider actors, PaymentOutcomeClient payment, OrderCreationGateway orders,
                                   OrderFeeGateway fees, FeeRefundReceiptGateway receipts, OrderCreationCodec codec, AcquireOrderConverter policies,
                                   FeeRefundConverter converter, OrderCreationTransactions transactions, IdGenerator ids,
                                   @Qualifier("acquireClock") Clock clock, @Value("${loadup.modules.acquire.payment-service-id:payment}") String paymentService) {
        this.actors = actors;
        this.payment = payment;
        this.orders = orders;
        this.fees = fees;
        this.receipts = receipts;
        this.codec = codec;
        this.policies = policies;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
        this.paymentService = DomainChecks.id(paymentService);
    }

    @Override
    public FeeRefundAckDTO accept(EventEnvelope<OrderRefundSummaryEventDTO> event) {
        var actor = actors.current();
        if (!paymentService.equals(actor.serviceId()) || !actor.permissions().contains("pay:acquire:fee:refund"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            if (event == null || event.data() == null || !TYPE.equals(event.eventType()) || event.aggregateVersion() <= 0)
                throw new CommonException(INVALID_ARGUMENT);
            event.data().requireEnvelope(event);
        } catch (DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (!payment.supportsTenant(actor.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.withTenantContext(actor.tenantId(), () -> {
            String digest = digest(actor, event);
            var known = receipts.find(actor.tenantId(), event.deliveryId());
            if (known.isPresent()) return replay(actor, known.get(), digest);
            var incoming = event.data();
            var original = orders.findOrder(actor.tenantId(), incoming.orderId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var verified = payment.refundSummary(actor.tenantId(), incoming.orderId()); // No database transaction or locks.
            if (verified == null || !verified.orderId().equals(incoming.orderId()) || !verified.payeeMerchantId().equals(incoming.payeeMerchantId())
                    || !verified.currency().equals(incoming.currency()) || !verified.payeeMerchantId().equals(original.order().payeeMerchantId())
                    || !verified.currency().equals(original.order().amount().getCurrencyValue())
                    || verified.refundSummaryVersion() < incoming.refundSummaryVersion() || verified.refundedMinor() < incoming.refundedMinor()
                    || verified.refundedMinor() > original.order().amount().getCent()
                    || verified.refundReservedMinor() > original.order().amount().getCent() - verified.refundedMinor()
                    || incoming.refundedMinor() > original.order().amount().getCent()
                    || incoming.refundReservedMinor() > original.order().amount().getCent() - incoming.refundedMinor()
                    || verified.refundSummaryVersion() == incoming.refundSummaryVersion() && !verified.equals(incoming))
                throw new CommonException(SNAPSHOT_INVALID);
            return transactions.writeLocal(actor.tenantId(), () -> {
                var existing = receipts.find(actor.tenantId(), event.deliveryId());
                if (existing.isPresent()) return replay(actor, existing.get(), digest);
                var stored = orders.lockOrder(actor.tenantId(), verified.orderId());
                if (!stored.order().payeeMerchantId().equals(verified.payeeMerchantId())
                        || !stored.order().amount().equals(original.order().amount()))
                    throw new CommonException(SNAPSHOT_INVALID);
                var admission = codec.decodeAdmission(stored.admissionPayload(), stored.payloadHash());
                if (!admission.orderId().equals(stored.order().id()) || !admission.payeeMerchantId().equals(stored.order().payeeMerchantId())
                        || !admission.admissionId().equals(stored.admission().admissionId()))
                    throw new CommonException(SNAPSHOT_INVALID);
                var at = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
                fees.applyRefundBasis(actor.tenantId(), stored.order(), policies.toFeePolicy(admission.terms().fee()),
                        verified.refundSummaryVersion(), verified.refundedMinor(), event.businessEventId(), at);
                var receipt = converter.toReceipt(ids.generate(), event, verified.refundSummaryVersion(), digest, actor.serviceId(), at);
                receipts.insert(actor.tenantId(), receipt);
                return converter.toAck(receipt);
            });
        });
    }

    private FeeRefundAckDTO replay(AcquireServiceActor actor, FeeRefundReceipt receipt, String digest) {
        if (!actor.serviceId().equals(receipt.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!receipt.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toAck(receipt);
    }

    private String digest(AcquireServiceActor actor, EventEnvelope<OrderRefundSummaryEventDTO> e) {
        var d = e.data();
        var framed = new StringBuilder("fee-refund-v1/");
        for (var value : List.of(actor.tenantId(), actor.serviceId(), e.deliveryId(), e.businessEventId(), e.producer(), e.eventType(),
                Integer.toString(e.schemaVersion()), e.aggregateId(), Long.toString(e.aggregateVersion()), e.occurredAt().toInstant().toString(),
                d.orderId(), d.payeeMerchantId(), d.currency(), Long.toString(d.refundedMinor()), Long.toString(d.refundReservedMinor()), Long.toString(d.refundSummaryVersion())))
            framed.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
        return codec.checksum(framed.toString());
    }
}
