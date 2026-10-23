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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.app.converter.RefundProjectionConverter;
import io.github.loadup.pay.payment.app.port.RefundSnapshotReader;
import io.github.loadup.pay.payment.domain.gateway.RefundSummaryGateway;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class RefundProjectionEvents {
    public static final String CHILD = "pay.payment.refund.realtime.v1";
    public static final String SUMMARY = "pay.payment.refund-summary.realtime.v1";
    private final RefundSummaryGateway summaries;
    private final RefundSnapshotReader reader;
    private final RefundProjectionConverter converter;
    private final OutboxTemplate outbox;
    private final IdGenerator ids;

    public RefundProjectionEvents(RefundSummaryGateway summaries, RefundSnapshotReader reader, RefundProjectionConverter converter,
                                  OutboxTemplate outbox, IdGenerator ids) {
        this.summaries = summaries;
        this.reader = reader;
        this.converter = converter;
        this.outbox = outbox;
        this.ids = ids;
    }

    public void planned(String tenant, StoredRefundRequest request, Money orderAmount) {
        writable(tenant);
        var aggregate = request.request();
        var prior = summaries.lock(tenant, aggregate.orderId());
        var before = prior.orElseGet(() -> OrderRefundSummary.init(aggregate.orderId(), aggregate.payeeMerchantId(), orderAmount));
        if (!before.payeeMerchantId().equals(aggregate.payeeMerchantId()) || !before.orderAmount().equals(orderAmount))
            throw new CommonException(SNAPSHOT_INVALID);
        var next = before.reserve(aggregate.amount().getCent());
        if (prior.isEmpty()) summaries.insert(tenant, next);
        else summaries.update(tenant, before, next);
        for (var child : request.allocations())
            publishChild(tenant, aggregate.orderId(), aggregate.payeeMerchantId(), child.refund().id(), child.refund().version());
        publishSummary(tenant, next);
    }

    public void changed(String tenant, RefundProgressChange change) {
        writable(tenant);
        publishChild(tenant, change.request().orderId(), change.request().payeeMerchantId(), change.refund().id(), change.refund().version());
        if (!change.refund().terminal()) return;
        var before = summaries.lock(tenant, change.request().orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!before.payeeMerchantId().equals(change.request().payeeMerchantId())
                || !before.orderAmount().getCurrencyValue().equals(change.refund().amount().getCurrencyValue()))
            throw new CommonException(SNAPSHOT_INVALID);
        var next = before.settle(change.refund().amount().getCent(), change.refund().state() == RefundState.SUCCEEDED);
        summaries.update(tenant, before, next);
        publishSummary(tenant, next);
    }

    private void publishChild(String tenant, String order, String merchant, String refundId, long version) {
        var facts = reader.child(tenant, refundId);
        if (facts == null || !refundId.equals(facts.refundId()) || !order.equals(facts.orderId()) || !merchant.equals(facts.payeeMerchantId())
                || facts.sourceVersion() != version) throw new CommonException(SNAPSHOT_INVALID);
        final io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO data;
        try {
            data = converter.toEvent(facts);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        send(tenant, CHILD, refundId, version, facts.sourceUpdatedAt(), data);
    }

    private void publishSummary(String tenant, OrderRefundSummary expected) {
        var facts = reader.summary(tenant, expected.orderId());
        if (facts == null || !expected.equals(facts.summary())) throw new CommonException(SNAPSHOT_INVALID);
        send(tenant, SUMMARY, expected.orderId(), expected.version(), facts.sourceUpdatedAt(), converter.toEvent(facts.summary()));
    }

    private <T> void send(String tenant, String type, String id, long version, java.time.LocalDateTime at, T data) {
        var envelope = new PayEventEnvelope<>(ids.generate(), ids.generate(), "payment", type, 1, id, version, BusinessTime.external(at), data);
        outbox.publish(new OutboxMessage(tenant, type, 1, id, JsonUtil.toJson(envelope)));
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
