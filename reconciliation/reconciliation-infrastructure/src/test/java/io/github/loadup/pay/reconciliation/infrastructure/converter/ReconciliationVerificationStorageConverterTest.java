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
package io.github.loadup.pay.reconciliation.infrastructure.converter;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationDisposition;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationOutcome;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationVerificationStorageConverterTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(ReconciliationVerificationStorageConverter.class.getPackageName());
    final ReconciliationVerificationStorageConverter converter = spring.getBean(ReconciliationVerificationStorageConverter.class);
    final ReconciliationKey key = new ReconciliationKey(ReconciliationRecordKind.PAYMENT, "transaction", null);
    final ReconciliationVerificationRequest initial = new ReconciliationVerificationRequest("request", new ReconciliationReviewReference("difference", "batch", "run", "a".repeat(64), key.fingerprint()), "proposal", "approval", 2L, "ops", "key", "b".repeat(64),
            new ReconciliationScope(Channel.WECHAT, "c".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116"), key, "owner-request", "delivery", ReconciliationVerificationState.INIT, null, 0L);

    @AfterEach
    void close() {
        spring.close();
    }

    @Test
    void initNeverAcquiresImplicitZeroOrPartialReceiptMetadata() {
        var row = converter.toDO("tenant", initial);
        assertNull(row.getStartedEpochMicros());
        assertNull(row.getAmountMinor());
        assertNull(row.getSourceVersion());
        assertNull(row.getAcceptedEpochMicros());
        assertEquals(initial, converter.toModel(row));
        row.setStartedEpochMicros(0L);
        assertThrows(CommonException.class, () -> converter.toModel(row));
    }

    @Test
    void coverageGapAndObservedReceiptRoundtripWithoutRewritingFrozenQuery() {
        var gap = new ReconciliationVerificationReceipt(ReconciliationVerificationDisposition.NOT_SUBMITTED, null, null, null, null, null, null, null, null, null, null, null, null, 1000000L, 2000000L, null);
        var gapRow = converter.toDO("tenant", initial.complete(gap));
        assertNull(gapRow.getAmountMinor());
        assertNull(gapRow.getOutcome());
        assertEquals(initial.complete(gap), converter.toModel(gapRow));
        var receipt = new ReconciliationVerificationReceipt(ReconciliationVerificationDisposition.OBSERVED, "revision", "payment", "attempt", null, 3L, "SUCCEEDED", ReconciliationVerificationOutcome.SUCCEEDED,
                Money.ofMinor(9007199254740993L, "CNY"), "actual-transaction", null, 1000000L, "d".repeat(64), 2000000L, 3000000L, null);
        var row = converter.toDO("tenant", initial.complete(receipt));
        assertEquals(initial.complete(receipt), converter.toModel(row));
        assertEquals(initial, converter.toInitial(converter.toModel(row)));
        assertEquals("transaction", row.getChannelTransactionNo());
        assertEquals("actual-transaction", row.getObservedChannelTransactionNo());
    }
}
