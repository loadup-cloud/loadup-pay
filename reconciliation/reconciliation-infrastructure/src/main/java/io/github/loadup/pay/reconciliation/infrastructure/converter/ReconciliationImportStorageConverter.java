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

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationImportDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationImportStorageConverter {
    ReconciliationScope toScope(ReconciliationImportDO row);

    @Mapping(target = "size", source = "fileSize")
    @Mapping(target = "sha256", source = "sourceSha256")
    ReconciliationFileSnapshot toFile(ReconciliationImportDO row);

    @Mapping(target = "merchantId", source = "statementMerchantId")
    @Mapping(target = "subMerchantId", source = "statementSubMerchantId")
    ReconciliationStatementAccount toAccount(ReconciliationImportDO row);

    @Mapping(target = "scope", source = "row")
    @Mapping(target = "statementAccount", source = "row")
    @Mapping(target = "file", source = "row")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "seal", ignore = true)
    @Mapping(target = "reject", ignore = true)
    ReconciliationImportRequest toModel(ReconciliationImportDO row);

    @Mapping(target = "state", constant = "INIT")
    @Mapping(target = "version", constant = "0L")
    @Mapping(target = "artifactId", ignore = true)
    @Mapping(target = "rejectionCode", ignore = true)
    @Mapping(target = "seal", ignore = true)
    @Mapping(target = "reject", ignore = true)
    ReconciliationImportRequest toInitial(ReconciliationImportRequest value);

    @Mapping(target = "batchId", source = "value.batchId")
    @Mapping(target = "callerServiceId", source = "value.callerServiceId")
    @Mapping(target = "requestKey", source = "value.requestKey")
    @Mapping(target = "requestDigest", source = "value.requestDigest")
    @Mapping(target = "channel", source = "value.scope.channel")
    @Mapping(target = "accountScopeHash", source = "value.scope.accountScopeHash")
    @Mapping(target = "billDate", source = "value.scope.billDate")
    @Mapping(target = "statementProfile", source = "value.scope.statementProfile")
    @Mapping(target = "statementMerchantId", source = "value.statementAccount.merchantId")
    @Mapping(target = "statementSubMerchantId", source = "value.statementAccount.subMerchantId")
    @Mapping(target = "fileId", source = "value.file.fileId")
    @Mapping(target = "storageId", source = "value.file.storageId")
    @Mapping(target = "fileSize", source = "value.file.size")
    @Mapping(target = "sourceSha256", source = "value.file.sha256")
    @Mapping(target = "sourceKind", source = "value.sourceKind")
    @Mapping(target = "taskId", source = "value.taskId")
    @Mapping(target = "state", source = "value.state")
    @Mapping(target = "artifactId", source = "value.artifactId")
    @Mapping(target = "rejectionCode", source = "value.rejectionCode")
    @Mapping(target = "rowVersion", source = "value.version")
    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ReconciliationImportDO toDO(String tenant, ReconciliationImportRequest value);
}
