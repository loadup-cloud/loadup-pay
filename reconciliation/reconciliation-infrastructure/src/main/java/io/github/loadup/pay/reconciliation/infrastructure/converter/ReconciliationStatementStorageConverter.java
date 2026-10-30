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
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationOfficialReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationStatementDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationStatementStorageConverter {
    ReconciliationScope toScope(ReconciliationStatementDO row);

    @Mapping(target = "merchantId", source = "statementMerchantId")
    @Mapping(target = "subMerchantId", source = "statementSubMerchantId")
    ReconciliationStatementAccount toAccount(ReconciliationStatementDO row);

    @Mapping(target = "size", source = "fileSize")
    @Mapping(target = "sha256", source = "sourceSha256")
    ReconciliationFileSnapshot content(ReconciliationStatementDO row);

    @Mapping(target = "fileId", source = "proofFileId")
    @Mapping(target = "storageId", source = "proofStorageId")
    @Mapping(target = "size", source = "proofFileSize")
    @Mapping(target = "sha256", source = "proofSha256")
    ReconciliationFileSnapshot evidence(ReconciliationStatementDO row);

    @Mapping(target = "content", source = "row", qualifiedByName = "contentFile")
    @Mapping(target = "evidence", source = "row", qualifiedByName = "evidenceFile")
    ReconciliationOfficialReceipt toReceipt(ReconciliationStatementDO row);

    @Named("contentFile")
    default ReconciliationFileSnapshot contentFile(ReconciliationStatementDO row) {
        return content(row);
    }

    @Named("evidenceFile")
    default ReconciliationFileSnapshot evidenceFile(ReconciliationStatementDO row) {
        return evidence(row);
    }

    @Mapping(target = "scope", source = "row")
    @Mapping(target = "statementAccount", source = "row")
    @Mapping(target = "receipt", expression = "java(receipt(row))")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "ready", ignore = true)
    ReconciliationStatementRequest toModel(ReconciliationStatementDO row);

    default ReconciliationOfficialReceipt receipt(ReconciliationStatementDO row) {
        if ("READY".equals(row.getState())) return toReceipt(row);
        if (row.getFormat() != null || row.getRequestSha256() != null || row.getOfficialHashType() != null || row.getOfficialHash() != null
                || row.getReceivedEpochMicros() != null || row.getDownloadedEpochMicros() != null || row.getFileId() != null || row.getStorageId() != null
                || row.getFileSize() != null || row.getSourceSha256() != null || row.getProofFileId() != null || row.getProofStorageId() != null
                || row.getProofFileSize() != null || row.getProofSha256() != null)
            throw new CommonException(SNAPSHOT_INVALID);
        return null;
    }

    @Mapping(target = "state", constant = "INIT")
    @Mapping(target = "receipt", ignore = true)
    @Mapping(target = "version", constant = "0L")
    @Mapping(target = "ready", ignore = true)
    ReconciliationStatementRequest toInitial(ReconciliationStatementRequest value);

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
    @Mapping(target = "bindingRevisionId", source = "value.bindingRevisionId")
    @Mapping(target = "maxBytes", source = "value.maxBytes")
    @Mapping(target = "taskId", source = "value.taskId")
    @Mapping(target = "state", source = "value.state")
    @Mapping(target = "rowVersion", source = "value.version")
    @Mapping(target = "format", source = "value.receipt.format")
    @Mapping(target = "requestSha256", source = "value.receipt.requestSha256")
    @Mapping(target = "officialHashType", source = "value.receipt.officialHashType")
    @Mapping(target = "officialHash", source = "value.receipt.officialHash")
    @Mapping(target = "receivedEpochMicros", source = "value.receipt", qualifiedByName = "receivedMicros")
    @Mapping(target = "downloadedEpochMicros", source = "value.receipt", qualifiedByName = "downloadedMicros")
    @Mapping(target = "fileId", source = "value.receipt.content.fileId")
    @Mapping(target = "storageId", source = "value.receipt.content.storageId")
    @Mapping(target = "fileSize", source = "value.receipt", qualifiedByName = "contentSize")
    @Mapping(target = "sourceSha256", source = "value.receipt.content.sha256")
    @Mapping(target = "proofFileId", source = "value.receipt.evidence.fileId")
    @Mapping(target = "proofStorageId", source = "value.receipt.evidence.storageId")
    @Mapping(target = "proofFileSize", source = "value.receipt", qualifiedByName = "evidenceSize")
    @Mapping(target = "proofSha256", source = "value.receipt.evidence.sha256")
    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ReconciliationStatementDO toDO(String tenant, ReconciliationStatementRequest value);

    @Named("receivedMicros")
    default Long receivedMicros(ReconciliationOfficialReceipt value) {
        return value == null ? null : value.receivedEpochMicros();
    }

    @Named("downloadedMicros")
    default Long downloadedMicros(ReconciliationOfficialReceipt value) {
        return value == null ? null : value.downloadedEpochMicros();
    }

    @Named("contentSize")
    default Long contentSize(ReconciliationOfficialReceipt value) {
        return value == null ? null : value.content().size();
    }

    @Named("evidenceSize")
    default Long evidenceSize(ReconciliationOfficialReceipt value) {
        return value == null ? null : value.evidence().size();
    }

}
