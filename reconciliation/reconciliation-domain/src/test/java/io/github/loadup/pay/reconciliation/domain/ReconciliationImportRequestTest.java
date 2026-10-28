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
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSourceKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationImportRequestTest {
    private final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), "parser");
    private final ReconciliationImportRequest initial = ReconciliationImportRequest.init("import", "batch", "ops", "request", "b".repeat(64), scope,
            new ReconciliationStatementAccount("1234567890", null), new ReconciliationFileSnapshot("file", "storage", 100, "c".repeat(64)), "task");

    @Test
    void manualSourceCannotBeSealedByDifferentIdentityHashScopeOrSide() {
        var valid = manifest("import", "import", "c".repeat(64), ReconciliationSide.CHANNEL);
        var sealed = initial.seal(valid);
        assertEquals(ReconciliationSourceKind.MANUAL_UPLOAD, sealed.sourceKind());
        assertEquals(1L, sealed.version());
        assertSame(sealed, sealed.seal(valid));
        assertThrows(DomainViolation.class, () -> initial.seal(manifest("other", "import", "c".repeat(64), ReconciliationSide.CHANNEL)));
        assertThrows(DomainViolation.class, () -> initial.seal(manifest("import", "other", "c".repeat(64), ReconciliationSide.CHANNEL)));
        assertThrows(DomainViolation.class, () -> initial.seal(manifest("import", "import", "d".repeat(64), ReconciliationSide.CHANNEL)));
        assertThrows(DomainViolation.class, () -> initial.seal(manifest("import", "import", "c".repeat(64), ReconciliationSide.LEDGER)));
        assertThrows(DomainViolation.class, () -> sealed.reject("INPUT_INVALID"));
    }

    @Test
    void rejectedOriginalIsImmutableAndNeverBecomesAnEmptySuccessfulArtifact() {
        var rejected = initial.reject("INPUT_INVALID");
        assertNull(rejected.artifactId());
        assertEquals(ReconciliationImportState.REJECTED, rejected.state());
        assertSame(rejected, rejected.reject("INPUT_INVALID"));
        assertThrows(DomainViolation.class, () -> rejected.reject("ANOTHER_REASON"));
        assertThrows(DomainViolation.class, () -> rejected.seal(manifest("import", "import", "c".repeat(64), ReconciliationSide.CHANNEL)));
        assertThrows(DomainViolation.class, () -> new ReconciliationImportRequest(initial.id(), initial.batchId(), initial.callerServiceId(),
                initial.requestKey(), initial.requestDigest(), scope, initial.statementAccount(), initial.file(), initial.sourceKind(), initial.taskId(),
                ReconciliationImportState.INIT, null, null, 1L));
    }

    private ReconciliationManifest manifest(String artifact, String capture, String sha, ReconciliationSide side) {
        return new ReconciliationManifest(side, scope, artifact, sha, ReconciliationDataset.normalizedHash(scope, side, "parser", List.of()), capture,
                "parser", ReconciliationTotals.count(List.of()));
    }
}
