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

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;

import java.time.LocalDateTime;
import java.util.List;

final class RefundResultFixtures {
    static final LocalDateTime NOW = AttemptFixtures.NOW;

    static StoredRefundRequest initial() {
        var first = RefundOrder.init("refund", "request", "payment", DomainChecks.cnyAmount(1000, "CNY"), "refund-request");
        var second = RefundOrder.init("refund2", "request", "payment2", DomainChecks.cnyAmount(500, "CNY"), "refund-request2");
        var a = new StoredRefundChild(first, 1, AttemptFixtures.prepared().attempt().identity(), "original-transaction", NOW.minusHours(1));
        var b = new StoredRefundChild(second, 2, new ChannelPaymentIdentity("payment2", "attempt2", Channel.WECHAT, "revision2",
                "b".repeat(64), "original-request2"), "original-transaction2", NOW.minusMinutes(30));
        var request = RefundRequest.init("request", "order", "merchant", "R001", DomainChecks.cnyAmount(1500, "CNY"), List.of(first, second));
        return new StoredRefundRequest(request, "CUSTOMER_REQUEST", "c".repeat(64), "d".repeat(64), "admission", "{}", "e".repeat(64),
                NOW.minusMinutes(1), List.of(a, b));
    }

    static PaymentRefundCapacity capacity() {
        return new PaymentRefundCapacity("payment", "order", "merchant", NOW.minusHours(1), DomainChecks.cnyAmount(4000, "CNY"), 0, 1000, 1);
    }

    static RefundObservation observation(ChannelRefundOutcome.Kind kind, String key, RefundObservation.Source source) {
        var child = initial().allocations().getFirst();
        return new RefundObservation("refund", child.original(), child.refund().channelRefundNo(), child.channelTransactionNo(), kind,
                child.refund().amount(), kind == ChannelRefundOutcome.Kind.SUCCEEDED ? "refund-transaction" : null,
                kind == ChannelRefundOutcome.Kind.SUCCEEDED ? NOW : null, source, key, "f".repeat(64));
    }
}
