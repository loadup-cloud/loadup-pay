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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationOfficialReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationStatementStorageConverterTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(ReconciliationStatementStorageConverter.class.getPackageName());
    final ReconciliationStatementStorageConverter converter = spring.getBean(ReconciliationStatementStorageConverter.class);
    final ReconciliationStatementRequest initial = ReconciliationStatementRequest.init("source", "batch", "ops", "request", "b".repeat(64),
            new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116"),
            new ReconciliationStatementAccount("1234567890", null), "revision", 1024, "download-task");

    @AfterEach
    void close() {
        spring.close();
    }

    @Test
    void initialAndSupersededNeverAcquireImplicitZeroReceiptFields() {
        var row = converter.toDO("tenant", initial);
        assertNull(row.getReceivedEpochMicros());
        assertNull(row.getDownloadedEpochMicros());
        assertNull(row.getFileSize());
        assertNull(row.getProofFileSize());
        assertNull(row.getFormat());
        assertNull(row.getProofSha256());
        assertEquals(initial, converter.toModel(row));
        row = converter.toDO("tenant", initial.supersede());
        assertEquals(initial.supersede(), converter.toModel(row));
        row.setReceivedEpochMicros(0L);
        var corrupt = row;
        assertThrows(CommonException.class, () -> converter.toModel(corrupt));
    }

    @Test
    void completeReceiptRoundtripsWithExactProtocolInstantsAndOriginalPrivateFiles() {
        var receipt = new ReconciliationOfficialReceipt("revision", "wechat-tradebill-grant-v1", "e".repeat(64), "SHA1", "f".repeat(40),
                1793131200123456L, 1793131201123456L, new ReconciliationFileSnapshot("file", "storage", 100, "c".repeat(64)), new ReconciliationFileSnapshot("proof", "proof-storage", 200, "d".repeat(64)));
        var ready = initial.ready(receipt);
        var row = converter.toDO("tenant", ready);
        assertEquals(ready, converter.toModel(row));
        assertEquals(Long.valueOf(receipt.receivedEpochMicros()), row.getReceivedEpochMicros());
        assertEquals(initial, converter.toInitial(ready));
        assertEquals("proof-storage", row.getProofStorageId());
        row.setState("INIT");
        row.setRowVersion(0L);
        assertThrows(CommonException.class, () -> converter.toModel(row));
    }
}
