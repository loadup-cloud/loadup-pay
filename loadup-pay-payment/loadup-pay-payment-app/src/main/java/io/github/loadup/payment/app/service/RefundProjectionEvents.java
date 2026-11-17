/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.payment.app.service;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.contracts.event.BusinessFact;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.contracts.event.payment.RefundSnapshotEventDTO;
import io.github.loadup.contracts.event.payment.RefundState;
import io.github.loadup.payment.app.converter.RefundProjectionConverter;
import io.github.loadup.payment.app.converter.RefundSettlementConverter;
import io.github.loadup.payment.app.dispatch.PaymentFactPublisher;
import io.github.loadup.payment.app.dispatch.PaymentFactRoutes;
import io.github.loadup.payment.app.model.RefundSnapshotFacts;
import io.github.loadup.payment.app.port.RefundSnapshotReader;
import io.github.loadup.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.payment.domain.gateway.RefundSummaryGateway;
import io.github.loadup.payment.domain.model.OrderRefundSummary;
import io.github.loadup.payment.domain.model.RefundOrder;
import io.github.loadup.payment.domain.model.RefundProgressChange;
import io.github.loadup.payment.domain.model.StoredRefundRequest;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RefundProjectionEvents {
    private final RefundSummaryGateway summaries;
    private final RefundSnapshotReader reader;
    private final RefundProjectionConverter converter;
    private final PaymentFactPublisher deliveries;
    private final IdGenerator ids;
    private final PaymentAcceptanceGateway payments;
    private final RefundSettlementConverter financial;
    private final RefundFinancialEvidenceVerifier evidence;

    public RefundProjectionEvents(
            RefundSummaryGateway summaries,
            RefundSnapshotReader reader,
            RefundProjectionConverter converter,
            TaskTemplate outbox,
            IdGenerator ids,
            PaymentAcceptanceGateway payments,
            RefundSettlementConverter financial,
            PaymentFactRoutes routes,
            RefundFinancialEvidenceVerifier evidence) {
        this.summaries = summaries;
        this.reader = reader;
        this.converter = converter;
        this.deliveries = new PaymentFactPublisher(outbox, ids, routes);
        this.ids = ids;
        this.payments = payments;
        this.financial = financial;
        this.evidence = evidence;
    }

    public void planned(StoredRefundRequest request, Money orderAmount) {
        writable();
        var aggregate = request.request();
        var prior = summaries.lock(aggregate.orderId());
        var before = prior.orElseGet(
                () -> OrderRefundSummary.init(aggregate.orderId(), aggregate.payeeMerchantId(), orderAmount));
        if (!before.payeeMerchantId().equals(aggregate.payeeMerchantId())
                || !before.orderAmount().equals(orderAmount)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var next = before.reserve(aggregate.amount().getCent());
        if (prior.isEmpty()) {
            summaries.insert(next);
        } else {
            summaries.update(before, next);
        }
        for (var child : request.allocations()) {
            publishChild(
                    aggregate.orderId(),
                    aggregate.payeeMerchantId(),
                    child.refund().id(),
                    child.refund().version());
        }
        publishSummary(next);
    }

    public void changed(RefundProgressChange change) {
        writable();
        var facts = publishChild(
                change.request().orderId(),
                change.request().payeeMerchantId(),
                change.refund().id(),
                change.refund().version());
        if (!change.refund().terminal()) {
            return;
        }
        if (!facts.refundRequestId().equals(change.request().id())
                || !facts.paymentId().equals(change.refund().paymentId())
                || !facts.state().equals(change.refund().state().name())
                || !facts.amount().equals(change.refund().amount())
                || !Objects.equals(facts.succeededAt(), change.succeededAt())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var before =
                summaries.lock(change.request().orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!before.payeeMerchantId().equals(change.request().payeeMerchantId())
                || !before.orderAmount()
                        .getCurrencyValue()
                        .equals(change.refund().amount().getCurrencyValue())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var next = before.settle(
                change.refund().amount().getCent(), change.refund().state() == RefundState.SUCCEEDED);
        summaries.update(before, next);
        publishSummary(next);
        publishFact(
                BusinessEventTypes.REFUND_PROGRESS,
                change.refund().id(),
                change.refund().version(),
                facts.sourceUpdatedAt(),
                converter.toMerchantEvent(facts, change.request().merchantRefundNo()));
        if (change.request().children().stream().allMatch(RefundOrder::terminal)) {
            publishFact(
                    BusinessEventTypes.REFUND_PROGRESS,
                    change.request().id(),
                    change.request().version(),
                    facts.sourceUpdatedAt(),
                    converter.toMerchantEvent(change.request()));
        }
        if (change.refund().state() == RefundState.SUCCEEDED) {
            var original =
                    payments.find(change.refund().paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var payment = original.payment();
            if (payment.state() != PaymentState.SUCCEEDED
                    || original.terminalEvidenceId() == null
                    || !payment.orderId().equals(change.request().orderId())
                    || !payment.payeeMerchantId().equals(change.request().payeeMerchantId())
                    || !payment.amount().equals(change.capacity().succeededAmount())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            publishFact(
                    BusinessEventTypes.REFUND_FINANCIAL,
                    change.refund().id(),
                    change.refund().version(),
                    facts.sourceUpdatedAt(),
                    financial
                            .toOutcome(change.refund(), change.request(), payment, change.capacity())
                            .withFinancialEvidence(evidence.freeze(change, original)));
        }
    }

    private RefundSnapshotFacts publishChild(String order, String merchant, String refundId, long version) {
        var facts = reader.child(refundId);
        if (facts == null
                || !refundId.equals(facts.refundId())
                || !order.equals(facts.orderId())
                || !merchant.equals(facts.payeeMerchantId())
                || facts.sourceVersion() != version) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        final RefundSnapshotEventDTO data;
        try {
            data = converter.toEvent(facts);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        publishFact(BusinessEventTypes.REFUND_SNAPSHOT, refundId, version, facts.sourceUpdatedAt(), data);
        return facts;
    }

    private void publishSummary(OrderRefundSummary expected) {
        var facts = reader.summary(expected.orderId());
        if (facts == null || !expected.equals(facts.summary())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        publishFact(
                BusinessEventTypes.ORDER_REFUND_SUMMARY,
                expected.orderId(),
                expected.version(),
                facts.sourceUpdatedAt(),
                converter.toEvent(facts.summary()));
    }

    private <T> void publishFact(String type, String id, long version, LocalDateTime at, T data) {
        deliveries.publish(
                new BusinessFact<>(ids.generate(), "payment", type, 1, id, version, BusinessTime.external(at), data));
    }

    private void writable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
