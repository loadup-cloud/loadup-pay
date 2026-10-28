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
package io.github.loadup.pay.realtime.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.app.converter.TradeProjectionConverter;
import io.github.loadup.pay.realtime.app.port.TradeProjectionCodec;
import io.github.loadup.pay.realtime.app.security.RealtimeActor;
import io.github.loadup.pay.realtime.app.security.RealtimeActorProvider;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.client.facade.TradeProjectionFacade;
import io.github.loadup.pay.realtime.domain.gateway.ProjectionReceiptGateway;
import io.github.loadup.pay.realtime.domain.gateway.TradeProjectionGateway;
import io.github.loadup.pay.realtime.domain.model.ProjectionAdvance;
import io.github.loadup.pay.realtime.domain.model.ProjectionReceipt;
import io.github.loadup.pay.realtime.domain.model.SourceVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.QUERY_FORBIDDEN;

@Service
public class TradeProjectionService implements TradeProjectionFacade {
    private final RealtimeActorProvider actors;
    private final TradeProjectionGateway projections;
    private final ProjectionReceiptGateway receipts;
    private final TradeProjectionCodec codec;
    private final TradeProjectionConverter converter;
    private final RealtimeTransactions transactions;
    private final IdGenerator ids;
    private final Clock clock;
    private final String acquireService, paymentService;

    public TradeProjectionService(RealtimeActorProvider actors, TradeProjectionGateway projections, ProjectionReceiptGateway receipts,
                                  TradeProjectionCodec codec, TradeProjectionConverter converter, RealtimeTransactions transactions, IdGenerator ids,
                                  @Qualifier("realtimeClock") Clock clock, @Value("${loadup.modules.realtime.acquire-service-id:acquire}") String acquireService,
                                  @Value("${loadup.modules.realtime.payment-service-id:payment}") String paymentService) {
        this.actors = actors;
        this.projections = projections;
        this.receipts = receipts;
        this.codec = codec;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
        this.acquireService = DomainChecks.id(acquireService);
        this.paymentService = DomainChecks.id(paymentService);
    }

    @Override
    public ProjectionAckDTO applyOrder(EventEnvelope<OrderSnapshotEventDTO> event) {
        var actor = actor(acquireService, "pay:realtime:projection:order");
        validate(() -> {
            event.data().requireEnvelope(event);
            return true;
        });
        var value = converter.toOrder(event.data(), new SourceVersion("acquire", "AcquireOrder", event.aggregateId(), event.aggregateVersion()), now());
        String hash = codec.hash(codec.encode(value));
        return consume(actor, event, hash, () -> projections.applyOrderIfNewer(actor.tenantId(), value, hash));
    }

    @Override
    public ProjectionAckDTO applyPayment(EventEnvelope<PaymentSnapshotEventDTO> event) {
        var actor = actor(paymentService, "pay:realtime:projection:payment");
        validate(() -> {
            event.data().requireEnvelope(event);
            return true;
        });
        var value = converter.toPayment(event.data(), new SourceVersion("payment", "PaymentOrder", event.aggregateId(), event.aggregateVersion()), now());
        String hash = codec.hash(codec.encode(value));
        return consume(actor, event, hash, () -> projections.applyPaymentIfNewer(actor.tenantId(), value, hash));
    }

    @Override
    public ProjectionAckDTO applyRefund(EventEnvelope<RefundSnapshotEventDTO> event) {
        var actor = actor(paymentService, "pay:realtime:projection:refund");
        validate(() -> {
            event.data().requireEnvelope(event);
            return true;
        });
        var value = converter.toRefund(event.data(), new SourceVersion("payment", "RefundOrder", event.aggregateId(), event.aggregateVersion()), now());
        String hash = codec.hash(codec.encode(value));
        return consume(actor, event, hash, () -> projections.applyRefundIfNewer(actor.tenantId(), value, hash));
    }

    @Override
    public ProjectionAckDTO applyRefundSummary(EventEnvelope<OrderRefundSummaryEventDTO> event) {
        var actor = actor(paymentService, "pay:realtime:projection:refund-summary");
        validate(() -> {
            event.data().requireEnvelope(event);
            if (!"pay.payment.refund-summary.realtime.v1".equals(event.eventType()))
                throw new CommonException(INVALID_ARGUMENT);
            return true;
        });
        var value = converter.toSummary(event.data(), new SourceVersion("payment", "OrderRefundSummary", event.aggregateId(), event.aggregateVersion()), now());
        String hash = codec.hash(codec.encode(value));
        return consume(actor, event, hash, () -> projections.applyRefundSummaryIfNewer(actor.tenantId(), value, hash));
    }

    private ProjectionAckDTO consume(RealtimeActor actor, EventEnvelope<?> event, String hash, Supplier<ProjectionAdvance> apply) {
        String digest = codec.requestDigest(actor.tenantId(), actor.serviceId(), event, hash);
        return transactions.writeLocal(actor.tenantId(), () -> {
            var prior = receipts.find(actor.tenantId(), event.producer(), event.deliveryId());
            if (prior.isPresent()) {
                var receipt = prior.get();
                if (!receipt.callerServiceId().equals(actor.serviceId()) || !receipt.requestDigest().equals(digest))
                    throw new CommonException(IDEMPOTENCY_CONFLICT);
                return converter.toAck(receipt);
            }
            var advance = apply.get();
            var receipt = new ProjectionReceipt(ids.generate(), event.deliveryId(), event.businessEventId(), event.producer(),
                    event.aggregateId(), event.aggregateVersion(), advance.projectedVersion(), advance.disposition(), digest, actor.serviceId(), now());
            receipts.insert(actor.tenantId(), receipt);
            return converter.toAck(receipt);
        });
    }

    private RealtimeActor actor(String service, String permission) {
        var actor = actors.current();
        if (!service.equals(actor.serviceId()) || !actor.permissions().contains(permission))
            throw new CommonException(QUERY_FORBIDDEN);
        return actor;
    }

    private void validate(Supplier<Boolean> work) {
        try {
            work.get();
        } catch (NullPointerException | DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
