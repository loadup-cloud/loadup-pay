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
package io.github.loadup.pay.payment.infrastructure.converter;

import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.PaymentCloseEvidence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PaymentCloseEvidenceStorageConverterTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(PaymentCloseEvidenceStorageConverter.class.getPackageName());
    final PaymentCloseEvidenceStorageConverter converter = spring.getBean(PaymentCloseEvidenceStorageConverter.class);

    @AfterEach
    void close() {
        spring.close();
    }

    @Test
    void immutableSourceScopeExactMoneyEpochAndTimeRoundTripWithoutFloatingPoint() {
        var identity = new ChannelPaymentIdentity("payment", "attempt", Channel.ALIPAY, "revision", "a".repeat(64), "original-request");
        var value = new PaymentCloseEvidence(identity, Money.ofMinor(9007199254740993L, "CNY"), "private-transaction",
                "close-receipt", "b".repeat(64), 9007199254740993L, LocalDateTime.of(2026, 10, 28, 18, 0, 0, 123456000));
        var row = converter.toDO("tenant", value);
        assertEquals("attempt", row.getId());
        assertEquals("ALIPAY", row.getChannel());
        assertEquals(9007199254740993L, row.getAmountMinor());
        assertEquals(9007199254740993L, row.getExecutionEpoch());
        assertFalse(row.getDeleted());
        assertEquals(value, converter.toModel(row));
        assertFalse(value.toString().contains("private-transaction"));
        assertFalse(row.toString().contains("private-transaction"));
    }
}
