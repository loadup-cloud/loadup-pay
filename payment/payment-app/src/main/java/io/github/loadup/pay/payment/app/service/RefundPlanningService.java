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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.payment.app.converter.RefundPlanningConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.security.RefundActor;
import io.github.loadup.pay.payment.app.security.RefundActorProvider;
import io.github.loadup.pay.payment.client.command.RefundCreateCommand;
import io.github.loadup.pay.payment.client.dto.RefundRequestDTO;
import io.github.loadup.pay.payment.client.facade.RefundFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundGateway;
import io.github.loadup.pay.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RefundCapacityReservation;
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundPlanItem;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
import io.github.loadup.pay.payment.domain.model.RefundRequestReference;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import io.github.loadup.pay.payment.domain.policy.FifoRefundPolicy;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
import io.github.loadup.pay.product.client.enums.RefundAllocationPolicy;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.REFUND_AMOUNT_EXCEEDED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;

@Service
public class RefundPlanningService implements RefundFacade {
    private final RefundActorProvider actors;
    private final RefundGateway refunds;
    private final PaymentAcceptanceGateway payments;
    private final PaymentResultGateway results;
    private final PaymentAttemptGateway attempts;
    private final PaymentAcceptanceCodec codec;
    private final RefundPlanningConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final FifoRefundPolicy fifo = new FifoRefundPolicy();
    private final RefundProjectionEvents projections;
    private final RefundRecoveryService recovery;

    public RefundPlanningService(RefundActorProvider actors, RefundGateway refunds, PaymentAcceptanceGateway payments,
                                 PaymentResultGateway results, PaymentAttemptGateway attempts, PaymentAcceptanceCodec codec, RefundPlanningConverter converter,
                                 PaymentAcceptanceTransactions transactions, IdGenerator ids, Validator validator, @Qualifier("paymentClock") Clock clock,
                                 RefundProjectionEvents projections, RefundRecoveryService recovery) {
        this.actors = actors;
        this.refunds = refunds;
        this.payments = payments;
        this.results = results;
        this.attempts = attempts;
        this.codec = codec;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.projections = projections;
        this.recovery = recovery;
    }

    @Override
    public RefundRequestDTO create(RefundCreateCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actor("pay:payment:refund:create");
        if (!actor.merchantId().equals(command.payeeMerchantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!command.explicitAllocations().isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        return transactions.writeLocal(actor.tenantId(), () -> {
            DomainChecks.id(command.orderId());
            DomainChecks.id(command.payeeMerchantId());
            DomainChecks.text(command.merchantRefundNo(), 64);
            DomainChecks.text(command.reasonCode(), 64);
            String digest = digest(actor, command);
            var known = refunds.merchantReference(actor.tenantId(), actor.merchantId(), command.merchantRefundNo());
            if (known.isPresent()) return replay(actor, known.get(), digest);
            lockParent(actor, command.orderId());
            // A competing key on another order must fail by digest, not expose or load that order's children.
            known = refunds.merchantReference(actor.tenantId(), actor.merchantId(), command.merchantRefundNo());
            if (known.isPresent()) return replayLocked(actor, known.get(), command.orderId(), digest);
            var capacities = refunds.lockCapacities(actor.tenantId(), command.orderId());
            if (capacities.isEmpty()) throw new CommonException(REFUND_AMOUNT_EXCEEDED);
            var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
            AdmittedOrderTermsDTO admitted = null;
            long grossCollected = 0;
            var claims = new HashMap<String, ChannelTransactionClaim>();
            var eligible = new ArrayList<PaymentRefundCapacity>();
            for (var capacity : capacities) {
                var accepted = payments.find(actor.tenantId(), capacity.paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                var payment = accepted.payment();
                var original = codec.decode(accepted);
                if (!actor.merchantId().equals(capacity.payeeMerchantId()) || !command.orderId().equals(capacity.orderId())
                        || payment.state() != PaymentState.SUCCEEDED || !payment.orderId().equals(command.orderId())
                        || !payment.payeeMerchantId().equals(actor.merchantId()) || !payment.amount().equals(capacity.succeededAmount()))
                    throw new CommonException(SNAPSHOT_INVALID);
                if (admitted == null) admitted = original.admittedTerms();
                else if (!admitted.equals(original.admittedTerms())) throw new CommonException(SNAPSHOT_INVALID);
                if (capacity.succeededAmount().getCent() > admitted.orderAmountMinor() - grossCollected)
                    throw new CommonException(SNAPSHOT_INVALID);
                grossCollected += capacity.succeededAmount().getCent();
                var claim = results.attemptClaim(actor.tenantId(), payment.successfulAttemptId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                var attempt = attempts.find(actor.tenantId(), payment.successfulAttemptId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!claim.tenantId().equals(actor.tenantId()) || !claim.identity().paymentId().equals(payment.id())
                        || !claim.identity().attemptId().equals(payment.successfulAttemptId()) || !claim.amount().equals(payment.amount())
                        || attempt.attempt().state() != PaymentAttemptState.SUCCEEDED || !attempt.attempt().identity().equals(claim.identity())
                        || !attempt.attempt().amount().equals(claim.amount())
                        || !claim.succeededAt().equals(capacity.succeededAt()) || now.isBefore(claim.succeededAt()))
                    throw new CommonException(SNAPSHOT_INVALID);
                claims.put(payment.id(), claim);
                if (now.isBefore(capacity.succeededAt().plusSeconds(admitted.terms().refund().windowSeconds())))
                    eligible.add(capacity);
            }
            if (admitted == null || !admitted.terms().refund().enabled()) throw new CommonException(ACTION_FORBIDDEN);
            var terms = admitted.terms().refund();
            if (terms.allocationPolicy() != RefundAllocationPolicy.FIFO)
                throw new CommonException(UNSUPPORTED_CAPABILITY);
            final List<RefundPlanItem> plan;
            try {
                plan = fifo.plan(command.orderId(), actor.merchantId(), DomainChecks.cnyAmount(command.amountMinor(), admitted.currency()),
                        terms.partialAllowed(), eligible);
            } catch (DomainViolation invalid) {
                if (invalid.reason() == DomainViolation.Reason.AMOUNT_EXCEEDED)
                    throw new CommonException(REFUND_AMOUNT_EXCEEDED);
                throw invalid;
            }
            String requestId = ids.generate();
            var children = new ArrayList<StoredRefundChild>();
            var reservations = new ArrayList<RefundCapacityReservation>();
            var byPayment = new HashMap<String, PaymentRefundCapacity>();
            eligible.forEach(c -> byPayment.put(c.paymentId(), c));
            for (var item : plan) {
                String refundId = ids.generate();
                // Stable channel request numbers are independent of UUID formatting and always fit provider limits.
                String channelNo = codec.checksum("refund/" + actor.tenantId() + "/" + refundId).substring(0, 32);
                var child = RefundOrder.init(refundId, requestId, item.paymentId(), item.amount(), channelNo);
                children.add(converter.toChild(child, item.sequenceNo(), claims.get(item.paymentId())));
                var previous = byPayment.get(item.paymentId());
                reservations.add(new RefundCapacityReservation(previous, previous.reserve(item.amount().getCent())));
            }
            var request = RefundRequest.init(requestId, command.orderId(), actor.merchantId(), command.merchantRefundNo(),
                    DomainChecks.cnyAmount(command.amountMinor(), admitted.currency()), children.stream().map(StoredRefundChild::refund).toList());
            String payload = JsonUtil.toJson(admitted);
            var stored = converter.toStored(request, command, digest, actor.subjectHash(), admitted.admissionId(), payload, codec.checksum(payload), now, children);
            refunds.insertPlan(actor.tenantId(), stored, reservations);
            projections.planned(actor.tenantId(), stored, DomainChecks.cnyAmount(admitted.orderAmountMinor(), admitted.currency()));
            recovery.register(actor.tenantId(), stored);
            return dto(stored);
        });
    }

    @Override
    public RefundRequestDTO detail(IdQuery query) {
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actor("pay:payment:refund:read");
        return transactions.writeLocal(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var reference = refunds.reference(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            if (!actor.merchantId().equals(reference.payeeMerchantId())) throw new CommonException(NOT_FOUND);
            lockParent(actor, reference.orderId());
            return dto(refunds.load(actor.tenantId(), reference.id()));
        });
    }

    private RefundRequestDTO replay(RefundActor actor, RefundRequestReference reference, String digest) {
        if (!reference.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
        lockParent(actor, reference.orderId());
        return replayLocked(actor, reference, reference.orderId(), digest);
    }

    private RefundRequestDTO replayLocked(RefundActor actor, RefundRequestReference reference, String lockedOrder, String digest) {
        if (!reference.requestDigest().equals(digest) || !reference.orderId().equals(lockedOrder))
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        var stored = refunds.load(actor.tenantId(), reference.id());
        if (!stored.requestDigest().equals(digest) || !actor.merchantId().equals(stored.request().payeeMerchantId()))
            throw new CommonException(SNAPSHOT_INVALID);
        return dto(stored);
    }

    private void lockParent(RefundActor actor, String order) {
        var parent = payments.lockParent(actor.tenantId(), order).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!actor.merchantId().equals(parent.payeeMerchantId())) throw new CommonException(NOT_FOUND);
        if (parent.gate().state() == GateState.INIT || !parent.gate().orderId().equals(order) || parent.gate().paymentId() != null)
            throw new CommonException(SNAPSHOT_INVALID);
        // FENCED stops collection. It does not prohibit refunding a completed or closed order.
    }

    private RefundActor actor(String permission) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (!actor.permissions().contains(permission)) throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    private RefundRequestDTO dto(StoredRefundRequest value) {
        return converter.toDTO(value.request(), converter.toAllocations(value.allocations()));
    }

    private String digest(RefundActor actor, RefundCreateCommand c) {
        var framed = new StringBuilder("refund-v1/");
        for (var value : List.of(actor.tenantId(), actor.merchantId(), c.orderId(), c.merchantRefundNo(), Long.toString(c.amountMinor()), c.reasonCode()))
            framed.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
        return codec.checksum(framed.toString());
    }
}
