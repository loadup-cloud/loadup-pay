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
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationStatementGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSourceKind;
import org.springframework.stereotype.Component;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Mandatory local provenance check for official imports. No file/network reads under the transaction.
 */
@Component
public class ReconciliationOfficialImports {
    private final ReconciliationStatementGateway sources;
    private final ReconciliationSourceFiles files;

    public ReconciliationOfficialImports(ReconciliationStatementGateway sources, ReconciliationSourceFiles files) {
        this.sources = sources;
        this.files = files;
    }

    public void verify(String tenant, ReconciliationImportRequest request) {
        if (request.sourceKind() == ReconciliationSourceKind.MANUAL_UPLOAD) {
            return;
        }
        var source = sources.find(tenant, request.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        try {
            source.verifyImport(request);
        } catch (io.github.loadup.commons.error.DomainViolation invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        files.verify(tenant, request.callerServiceId(), source.receipt().content(), source.id());
        files.verify(tenant, request.callerServiceId(), source.receipt().evidence(), source.id());
    }
}
