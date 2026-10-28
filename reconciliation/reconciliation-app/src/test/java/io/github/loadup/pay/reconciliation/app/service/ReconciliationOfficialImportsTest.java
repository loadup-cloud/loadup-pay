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
package io.github.loadup.pay.reconciliation.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationStatementGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationOfficialReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSourceKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReconciliationOfficialImportsTest {
    final ReconciliationStatementGateway sources = mock(ReconciliationStatementGateway.class);
    final ReconciliationSourceFiles files = mock(ReconciliationSourceFiles.class);
    final ReconciliationOfficialImports verifier = new ReconciliationOfficialImports(sources, files);
    final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116");
    final ReconciliationStatementAccount account = new ReconciliationStatementAccount("1234567890", null);
    final ReconciliationFileSnapshot file = new ReconciliationFileSnapshot("file", "storage", 100, "b".repeat(64));
    final ReconciliationFileSnapshot proof = new ReconciliationFileSnapshot("proof", "private-storage", 200, "c".repeat(64));
    final ReconciliationStatementRequest initial = ReconciliationStatementRequest.init("source", "batch", "ops", "request", "d".repeat(64), scope, account, "revision", 1024, "download-task");
    final ReconciliationImportRequest imported = new ReconciliationImportRequest("source", "batch", "ops", "official:source", "d".repeat(64), scope, account, file, ReconciliationSourceKind.OFFICIAL_DOWNLOAD, "transfer-task", ReconciliationImportState.INIT, null, null, 0L);

    @Test
    void kindAloneCannotEstablishOfficialOriginOrSkipPinnedProof() {
        when(sources.find("tenant", "source")).thenReturn(Optional.empty());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> verifier.verify("tenant", imported)).getResultCode());
        when(sources.find("tenant", "source")).thenReturn(Optional.of(initial));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> verifier.verify("tenant", imported)).getResultCode());
        verifyNoInteractions(files);
        var ready = initial.ready(new ReconciliationOfficialReceipt("revision", "wechat-tradebill-grant-v1", "e".repeat(64), "SHA1", "f".repeat(40), 1000000L, 2000000L, file, proof));
        when(sources.find("tenant", "source")).thenReturn(Optional.of(ready));
        verifier.verify("tenant", imported);
        verify(files).verify("tenant", "ops", file, "source");
        verify(files).verify("tenant", "ops", proof, "source");
    }

    @Test
    void manualImportsRemainExplicitManualSourcesWithoutSyntheticEvidence() {
        verifier.verify("tenant", ReconciliationImportRequest.init("manual", "batch", "ops", "request", "d".repeat(64), scope, account, file, "transfer-task"));
        verifyNoInteractions(sources, files);
    }
}
