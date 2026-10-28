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
package io.github.loadup.pay.reconciliation.domain;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationOfficialReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSourceKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationStatementRequestTest {
    final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116");
    final ReconciliationStatementAccount account = new ReconciliationStatementAccount("1234567890", null);
    final ReconciliationStatementRequest initial = ReconciliationStatementRequest.init("source", "batch", "ops", "request", "b".repeat(64), scope, account, "revision", 1024, "download-task");
    final ReconciliationFileSnapshot file = new ReconciliationFileSnapshot("file", "storage", 100, "c".repeat(64));
    final ReconciliationFileSnapshot proof = new ReconciliationFileSnapshot("proof", "proof-storage", 200, "d".repeat(64));
    final ReconciliationOfficialReceipt receipt = new ReconciliationOfficialReceipt("revision", "wechat-tradebill-grant-v1", "e".repeat(64), "SHA1", "f".repeat(40), 1000000L, 2000000L, file, proof);

    @Test
    void readyPinsFilesAndNativeDownloadIdentityAndNeverImpliesParsedOrMatched() {
        var ready = initial.ready(receipt);
        assertEquals(ReconciliationStatementState.INIT, initial.state());
        assertNull(initial.receipt());
        assertEquals(ReconciliationStatementState.READY, ready.state());
        assertEquals(1L, ready.version());
        assertEquals("download-task", ready.taskId());
        assertSame(ready, ready.ready(receipt));
        assertThrows(DomainViolation.class, ready::supersede);
        var imported = imported("source", "official:source", ReconciliationSourceKind.OFFICIAL_DOWNLOAD);
        assertEquals(ReconciliationImportState.INIT, imported.state());
        assertDoesNotThrow(() -> ready.verifyImport(imported));
        assertThrows(DomainViolation.class, () -> ready.verifyImport(imported("source", "official:source", ReconciliationSourceKind.MANUAL_UPLOAD)));
        assertThrows(DomainViolation.class, () -> ready.verifyImport(imported("different", "official:source", ReconciliationSourceKind.OFFICIAL_DOWNLOAD)));
        assertThrows(DomainViolation.class, () -> ready.verifyImport(imported("source", "request", ReconciliationSourceKind.OFFICIAL_DOWNLOAD)));
    }

    @Test
    void supersededHasNoFileAndCannotBecomeReadyOrReplacePinnedProvenance() {
        var superseded = initial.supersede();
        assertNull(superseded.receipt());
        assertSame(superseded, superseded.supersede());
        assertThrows(DomainViolation.class, () -> superseded.ready(receipt));
        var other = new ReconciliationOfficialReceipt("other-revision", receipt.format(), receipt.requestSha256(), receipt.officialHashType(), receipt.officialHash(), 1000000L, 2000000L, file, proof);
        assertThrows(DomainViolation.class, () -> initial.ready(other));
        var tooLarge = new ReconciliationOfficialReceipt("revision", receipt.format(), receipt.requestSha256(), receipt.officialHashType(), receipt.officialHash(), 1000000L, 2000000L, new ReconciliationFileSnapshot("file", "storage", 1025, "c".repeat(64)), proof);
        assertThrows(DomainViolation.class, () -> initial.ready(tooLarge));
        assertThrows(DomainViolation.class, () -> new ReconciliationOfficialReceipt("revision", receipt.format(), receipt.requestSha256(), "SHA1", receipt.officialHash(), 2000000L, 1000000L, file, proof));
    }

    ReconciliationImportRequest imported(String id, String key, ReconciliationSourceKind kind) {
        return new ReconciliationImportRequest(id, "batch", "ops", key, "b".repeat(64), scope, account, file, kind, "transfer-task", ReconciliationImportState.INIT, null, null, 0L);
    }
}
