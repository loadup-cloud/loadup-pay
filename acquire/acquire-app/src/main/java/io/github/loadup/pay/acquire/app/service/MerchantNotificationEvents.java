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
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.app.converter.MerchantNotificationConverter;
import io.github.loadup.pay.acquire.app.model.*;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotReader;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Optional;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;

/** Frozen merchant events and their first native wake-up join the source financial transaction. */
@Component
public class MerchantNotificationEvents {
    private final OrderCreationGateway orders;
    private final OrderCreationCodec admissions;
    private final OrderSnapshotReader facts;
    private final MerchantNotificationConverter converter;
    private final MerchantNotificationGateway deliveries;
    private final DelayedTaskTemplate tasks;
    private final IdGenerator ids;
    public MerchantNotificationEvents(OrderCreationGateway orders, OrderCreationCodec admissions, OrderSnapshotReader facts,
            MerchantNotificationConverter converter, MerchantNotificationGateway deliveries, DelayedTaskTemplate tasks, IdGenerator ids) {
        this.orders = orders; this.admissions = admissions; this.facts = facts; this.converter = converter;
        this.deliveries = deliveries; this.tasks = tasks; this.ids = ids;
    }
    public void order(String tenant, OrderSnapshotFacts header) {
        writable(tenant);
        String type = switch (header.state()) {
            case "PAID" -> "OrderFullyPaid";
            case "CLOSED" -> "OrderClosed";
            default -> null;
        };
        if (type != null) publish(tenant, header, type, "AcquireOrder", header.orderId(), header.sourceVersion(), null, header.amount().getCent());
    }
    public void payment(String tenant, String orderId, VerifiedPaymentOutcome payment) {
        writable(tenant);
        if (payment.kind() != VerifiedPaymentOutcome.Kind.SUCCEEDED) return;
        var header = facts.lockCurrent(tenant, orderId);
        if (!orderId.equals(payment.orderId()) || !orderId.equals(header.orderId()) || payment.amount().getCent() > header.paidMinor())
            throw new CommonException(SNAPSHOT_INVALID);
        publish(tenant, header, "PaymentSucceeded", "PaymentOrder", payment.paymentId(), payment.paymentVersion(), payment.paymentId(), payment.amount().getCent());
    }

    public MerchantNotificationRegistration refund(String tenant, EventEnvelope<RefundMerchantEventDTO> source) {
        writable(tenant);
        source.data().requireEnvelope(source);
        var data = source.data();
        var stored = orders.lockOrder(tenant, data.orderId());
        var order = stored.order();
        if (!order.id().equals(data.orderId()) || !order.payeeMerchantId().equals(data.payeeMerchantId())
                || !order.amount().getCurrencyValue().equals(data.currency()) || data.amountMinor() > order.paidAmountMinor())
            throw new CommonException(SNAPSHOT_INVALID);
        String type = data.merchantEventType();
        String eventId = eventId(tenant, type, source.aggregateId(), source.aggregateVersion());
        var policy = policy(stored);
        if (policy.isEmpty()) return new MerchantNotificationRegistration(eventId, "DISABLED");
        var envelope = new MerchantNotificationBody<>(eventId, type, 1, "payment", data.aggregateType(), source.aggregateId(),
                source.aggregateVersion(), source.occurredAt(), converter.toRefundData(data));
        var occurredAt = source.occurredAt().atZoneSameInstant(BusinessTime.zone()).toLocalDateTime();
        enqueue(tenant, order.id(), order.payeeMerchantId(), type, source.aggregateId(), source.aggregateVersion(),
                eventId, JsonUtil.toJson(envelope), policy.get(), occurredAt);
        return new MerchantNotificationRegistration(eventId, "ENQUEUED");
    }

    private void publish(String tenant, OrderSnapshotFacts header, String type, String aggregateType, String aggregate,
            long version, String paymentId, long amount) {
        var stored = orders.lockOrder(tenant, header.orderId());
        if (stored.order().version() != header.sourceVersion() || !stored.order().payeeMerchantId().equals(header.payeeMerchantId())
                || !stored.order().amount().equals(header.amount()) || stored.order().paidAmountMinor() != header.paidMinor()
                || !stored.order().state().name().equals(header.state())) throw new CommonException(SNAPSHOT_INVALID);
        var policy = policy(stored);
        if (policy.isEmpty()) return;
        String eventId = eventId(tenant, type, aggregate, version);
        var envelope = new MerchantNotificationBody<>(eventId, type, 1, "acquire", aggregateType, aggregate, version,
                BusinessTime.external(header.sourceUpdatedAt()), converter.toData(header, paymentId, amount));
        enqueue(tenant, header.orderId(), header.payeeMerchantId(), type, aggregate, version, eventId,
                JsonUtil.toJson(envelope), policy.get(), header.sourceUpdatedAt());
    }

    private Optional<MerchantNotificationPolicy> policy(StoredAcquireOrder stored) {
        var admitted = admissions.decodeAdmission(stored.admissionPayload(), stored.payloadHash());
        if (admitted == null || admitted.decision() != io.github.loadup.pay.product.client.enums.AdmissionDecision.ADMITTED
                || !stored.admission().admissionId().equals(admitted.admissionId()) || !stored.order().id().equals(admitted.orderId())
                || !stored.order().payeeMerchantId().equals(admitted.payeeMerchantId())
                || !stored.admission().requestDigest().equals(admitted.requestDigest()) || admitted.terms() == null)
            throw new CommonException(SNAPSHOT_INVALID);
        var terms = admitted.terms().notification();
        if (terms == null) throw new CommonException(SNAPSHOT_INVALID);
        return terms.enabled() ? Optional.of(converter.toPolicy(terms)) : Optional.empty();
    }

    private String eventId(String tenant, String type, String aggregate, long version) {
        return DigestUtils.sha256(JsonUtil.toJson(List.of("pay-merchant-event-v1", tenant, type, aggregate, version)));
    }

    private void enqueue(String tenant, String orderId, String merchantId, String type, String aggregate, long version,
            String eventId, String body, MerchantNotificationPolicy policy, java.time.LocalDateTime occurredAt) {
        String digest = DigestUtils.sha256(body);
        var prior = deliveries.findEvent(tenant, eventId, policy.targetRef());
        if (prior.isPresent()) {
            var existing = prior.get();
            if (!existing.body().equals(body) || !existing.bodyDigest().equals(digest) || !existing.policy().equals(policy)
                    || !existing.orderId().equals(orderId) || !existing.payeeMerchantId().equals(merchantId))
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            return;
        }
        var delivery = MerchantNotificationDelivery.init(ids.generate(), eventId, orderId, merchantId,
                type, aggregate, version, body, digest, policy, occurredAt);
        deliveries.insert(tenant, delivery);
        tasks.register(MerchantNotificationService.TYPE, new MerchantNotificationKey(delivery.id(), 0).bizId(), delivery.nextAttemptAt());
    }
    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
