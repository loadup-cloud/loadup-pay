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

import io.github.loadup.pay.common.AccessMode;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.common.Channel;
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Clock;
import java.time.LocalDateTime;

final class RealtimeFixtures {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 12, 0);

    static Clock clock() {
        return Clock.fixed(NOW.atZone(BusinessTime.ZONE).toInstant(), BusinessTime.ZONE);
    }

    static PayEventEnvelope<PaymentSnapshotEventDTO> payment(String delivery, long version, PaymentState state) {
        return new PayEventEnvelope<>(delivery, "business-event", "payment", "pay.payment.snapshot.realtime.v1", 1, "payment", version,
                BusinessTime.external(NOW), new PaymentSnapshotEventDTO("payment", "order", "merchant", 1000, "CNY", state,
                Channel.WECHAT, AccessMode.DIRECT, BusinessTime.external(NOW.minusMinutes(1)),
                state == PaymentState.SUCCEEDED ? BusinessTime.external(NOW) : null, version));
    }

    static final class Transactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
