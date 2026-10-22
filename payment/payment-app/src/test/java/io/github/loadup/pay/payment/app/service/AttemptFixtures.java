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

import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.domain.model.*;
import java.time.LocalDateTime;
import java.util.Set;

final class AttemptFixtures {
    static final LocalDateTime NOW = LocalDateTime.of(2026,10,22,12,0,0,123456000);
    static PaymentExecuteCommand command() { return PaymentFixtures.command(); }
    static ChannelBindingRevision binding() {
        var value = command();
        var mode = value.admittedTerms().terms().capabilities().stream()
                .filter(capability -> capability.channel() == value.choice().channel()
                        && capability.methods().contains(value.choice().method())).findFirst().orElseThrow().accessMode();
        ChannelAccountIdentity identity = value.choice().channel() == Channel.WECHAT
                ? new WechatAccountIdentity("app", "mchid", null, mode == AccessMode.DIRECT ? null : "sub-mchid")
                : new AlipayAccountIdentity("app", "seller", null,
                        mode == AccessMode.DIRECT ? null : "authorization", mode == AccessMode.DIRECT ? null : "v1");
        return new ChannelBindingRevision("binding-revision", value.payeeMerchantId(), value.choice().channel(), mode,
                "a".repeat(64), "credential", "v1", identity, Set.of(value.choice().method()));
    }
    static StoredPaymentAttempt prepared() {
        var command = command();
        var attempt = PaymentAttempt.init("attempt", command.paymentId(), 1,
                DomainChecks.amount(command.amountMinor(), command.currency()), binding(), command.choice().method(), "a".repeat(32));
        return new StoredPaymentAttempt(attempt, "operation", "acquire", "initial", "b".repeat(64), command.requestDigest(),
                command.preparedInputRef(), null, null, NOW, null, 0);
    }
    static StoredPaymentAcceptance accepted(boolean attempted) {
        var command = command();
        var payment = PaymentOrder.init(command.paymentId(), command.orderId(), command.allocationId(), command.payeeMerchantId(),
                DomainChecks.amount(command.amountMinor(), command.currency()), "operation", NOW);
        if (attempted) payment = payment.prepareAttempt(prepared().attempt(), null);
        return new StoredPaymentAcceptance(payment, command.admissionId(), command.requestDigest(), "acquire", "payload", "c".repeat(64), null);
    }
    static StoredPaymentGate gate(boolean parent) {
        var command = command();
        return new StoredPaymentGate(parent ? "parent" : "gate", command.payeeMerchantId(), "acquire",
                PaymentExecutionGate.init(command.orderId(), parent ? null : command.paymentId()).open(), 0);
    }
}
