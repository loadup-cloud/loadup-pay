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
package io.github.loadup.pay.reconciliation.app.port;

import io.github.loadup.pay.reconciliation.app.model.ReconciliationDifferencePage;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifferenceReason;

/** Page and count use this service's consistent read-only snapshot; no source-service lookup or locks. */
public interface ReconciliationDifferenceReader {
    ReconciliationDifferencePage page(String tenant, String batchId, ReconciliationRecordKind kind,
            ReconciliationDifferenceReason reason, int page, int size);
}
