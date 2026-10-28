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
package io.github.loadup.pay.payment.domain;

import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.PaymentCloseEvidence;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentCloseEvidenceTest {
    final ChannelPaymentIdentity identity = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "revision", "a".repeat(64), "original-request");
    final LocalDateTime at = LocalDateTime.of(2026, 10, 28, 18, 0);

    @Test
    void acknowledgementRequiresSubmittedEpochPositiveMoneyExactDigestAndMicrosecondTime() {
        assertThrows(RuntimeException.class, () -> new PaymentCloseEvidence(identity, Money.ofMinor(100L, "CNY"), null, "receipt", "b".repeat(64), 0L, at));
        assertThrows(RuntimeException.class, () -> new PaymentCloseEvidence(identity, Money.ofMinor(0L, "CNY"), null, "receipt", "b".repeat(64), 1L, at));
        assertThrows(RuntimeException.class, () -> new PaymentCloseEvidence(identity, Money.ofMinor(100L, "CNY"), null, "receipt", "invalid", 1L, at));
        assertThrows(RuntimeException.class, () -> new PaymentCloseEvidence(identity, Money.ofMinor(100L, "CNY"), null, "receipt", "b".repeat(64), 1L, at.withNano(1)));
        var proof = new PaymentCloseEvidence(identity, Money.ofMinor(100L, "CNY"), null, "receipt", "b".repeat(64), 1L, at);
        assertNull(proof.channelTransactionNo());
    }
}
