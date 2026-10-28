/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.app.converter.RefundNotificationConverter;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActor;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActorProvider;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.acquire.client.facade.RefundNotificationFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.gateway.RefundNotificationReceiptGateway;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.RefundNotificationReceipt;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Projects authenticated committed refund facts into notifications; never changes financial state.
 */
@Service
public class RefundNotificationService implements RefundNotificationFacade {
    private final AcquireServiceActorProvider actors;
    private final OrderCreationGateway orders;
    private final PaymentOutcomeGateway payments;
    private final RefundNotificationReceiptGateway receipts;
    private final MerchantNotificationEvents notifications;
    private final RefundNotificationConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Clock clock;
    private final String paymentService;

    public RefundNotificationService(AcquireServiceActorProvider actors, OrderCreationGateway orders,
                                     PaymentOutcomeGateway payments, RefundNotificationReceiptGateway receipts, MerchantNotificationEvents notifications,
                                     RefundNotificationConverter converter, OrderCreationTransactions transactions, IdGenerator ids,
                                     @Qualifier("acquireClock") Clock clock, @Value("${loadup.modules.acquire.payment-service-id:payment}") String paymentService) {
        this.actors = actors;
        this.orders = orders;
        this.payments = payments;
        this.receipts = receipts;
        this.notifications = notifications;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
        this.paymentService = DomainChecks.id(paymentService);
    }

    @Override
    public RefundNotificationAckDTO accept(EventEnvelope<RefundMerchantEventDTO> event) {
        var actor = actors.current();
        if (!paymentService.equals(actor.serviceId()) || !actor.permissions().contains("pay:acquire:refund:notification"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            if (event == null || event.data() == null) throw new CommonException(INVALID_ARGUMENT);
            event.data().requireEnvelope(event);
            if (event.occurredAt().getNano() % 1000 != 0) throw new CommonException(INVALID_ARGUMENT);
        } catch (DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        return transactions.withTenantContext(actor.tenantId(), () -> {
            String digest = DigestUtils.sha256(JsonUtil.toJson(List.of("refund-notification-inbox-v1", actor.tenantId(), actor.serviceId(), event)));
            var known = receipts.find(actor.tenantId(), event.deliveryId());
            if (known.isPresent()) return replay(actor, known.get(), digest);
            return transactions.writeLocal(actor.tenantId(), () -> {
                var existing = receipts.find(actor.tenantId(), event.deliveryId());
                if (existing.isPresent()) return replay(actor, existing.get(), digest);
                var data = event.data();
                var order = orders.lockOrder(actor.tenantId(), data.orderId()).order();
                if (!order.id().equals(data.orderId()) || !order.payeeMerchantId().equals(data.payeeMerchantId())
                        || !order.amount().getCurrencyValue().equals(data.currency()) || data.amountMinor() > order.amount().getCent())
                    throw new CommonException(SNAPSHOT_INVALID);
                // The payment-success projection may arrive after the refund projection; retry until it is durable.
                if (data.amountMinor() > order.paidAmountMinor()) throw new CommonException(DEPENDENCY_UNAVAILABLE);
                if ("RefundOrder".equals(data.aggregateType())) {
                    var allocation = payments.lockPayment(actor.tenantId(), data.orderId(), data.paymentId())
                            .orElseThrow(() -> new CommonException(DEPENDENCY_UNAVAILABLE)).allocation();
                    if (!allocation.orderId().equals(data.orderId()) || !allocation.paymentId().equals(data.paymentId())
                            || !allocation.amount().getCurrencyValue().equals(data.currency()) || data.amountMinor() > allocation.amount().getCent())
                        throw new CommonException(SNAPSHOT_INVALID);
                    if (allocation.state() != AllocationState.CONSUMED)
                        throw new CommonException(DEPENDENCY_UNAVAILABLE);
                }
                var registration = notifications.refund(actor.tenantId(), event);
                var receipt = converter.toReceipt(ids.generate(), event, registration, digest, actor.serviceId(),
                        LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
                receipts.insert(actor.tenantId(), receipt);
                return converter.toAck(receipt);
            });
        });
    }

    private RefundNotificationAckDTO replay(AcquireServiceActor actor, RefundNotificationReceipt receipt, String digest) {
        if (!actor.serviceId().equals(receipt.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!digest.equals(receipt.requestDigest())) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toAck(receipt);
    }
}
