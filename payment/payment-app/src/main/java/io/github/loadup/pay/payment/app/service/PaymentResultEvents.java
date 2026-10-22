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

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.outbox.*;
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.app.converter.PaymentResultConverter;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.List;

/** Each target has its own durable delivery; handlers must revalidate authority before financial side effects. */
@Component
public class PaymentResultEvents {
    public static final String ACQUIRE = "pay.payment.outcome.acquire.v1";
    public static final String LIMIT = "pay.payment.outcome.limit.v1";
    public static final String REALTIME = "pay.payment.outcome.realtime.v1";
    public static final String RISK = "pay.payment.outcome.risk.v1";
    private final OutboxTemplate outbox;
    private final PaymentResultConverter converter;
    private final IdGenerator ids;
    public PaymentResultEvents(OutboxTemplate outbox, PaymentResultConverter converter, IdGenerator ids) {
        this.outbox = outbox; this.converter = converter; this.ids = ids;
    }
    public void publish(String tenant, StoredPaymentAcceptance payment, boolean fenced, LocalDateTime occurredAt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new CommonException(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE);
        var data = converter.toEvent(payment, fenced);
        for (var type : List.of(ACQUIRE, LIMIT, REALTIME, RISK)) {
            var envelope = new PayEventEnvelope<>(ids.generate(), payment.terminalEvidenceId(), "payment", type, 1,
                    payment.payment().id(), payment.payment().version(), BusinessTime.external(occurredAt), data);
            outbox.publish(new OutboxMessage(tenant, type, 1, payment.payment().id(), JsonUtil.toJson(envelope)));
        }
    }
}
