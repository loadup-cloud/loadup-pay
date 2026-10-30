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
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReview;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewEvent;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewProposal;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationReviewDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationReviewEventDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationReviewProposalDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationReviewStorageConverter {
    @Mapping(target = "differenceId", source = "id")
    ReconciliationReviewReference reference(ReconciliationReviewDO row);

    ReconciliationReviewReference reference(ReconciliationReviewProposalDO row);

    ReconciliationReviewReference reference(ReconciliationReviewEventDO row);

    @Mapping(target = "reference", source = "row")
    @Mapping(target = "version", source = "rowVersion")
    ReconciliationReview head(ReconciliationReviewDO row);

    @Mapping(target = "reference", source = "row")
    @Mapping(target = "evidence", expression = "java(evidence(row))")
    ReconciliationReviewProposal proposal(ReconciliationReviewProposalDO row);

    @Mapping(target = "reference", source = "row")
    @Mapping(target = "version", source = "sourceVersion")
    ReconciliationReviewEvent event(ReconciliationReviewEventDO row);

    @Mapping(target = "fileId", source = "evidenceFileId")
    @Mapping(target = "storageId", source = "evidenceStorageId")
    @Mapping(target = "size", source = "evidenceSize")
    @Mapping(target = "sha256", source = "evidenceSha256")
    ReconciliationFileSnapshot toEvidence(ReconciliationReviewProposalDO row);

    default ReconciliationFileSnapshot evidence(ReconciliationReviewProposalDO row) {
        if (row.getEvidenceFileId() == null) {
            if (row.getEvidenceStorageId() != null || row.getEvidenceSize() != null || row.getEvidenceSha256() != null)
                throw new CommonException(SNAPSHOT_INVALID);
            return null;
        }
        return toEvidence(row);
    }

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.reference.differenceId")
    @Mapping(target = "batchId", source = "value.reference.batchId")
    @Mapping(target = "runId", source = "value.reference.runId")
    @Mapping(target = "resultHash", source = "value.reference.resultHash")
    @Mapping(target = "matchKeyHash", source = "value.reference.matchKeyHash")
    @Mapping(target = "state", source = "value.state")
    @Mapping(target = "proposalId", source = "value.proposalId")
    @Mapping(target = "lastEventId", source = "value.lastEventId")
    @Mapping(target = "rowVersion", source = "value.version")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ReconciliationReviewDO toDO(String tenant, ReconciliationReview value);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "differenceId", source = "value.reference.differenceId")
    @Mapping(target = "batchId", source = "value.reference.batchId")
    @Mapping(target = "runId", source = "value.reference.runId")
    @Mapping(target = "resultHash", source = "value.reference.resultHash")
    @Mapping(target = "matchKeyHash", source = "value.reference.matchKeyHash")
    @Mapping(target = "proposerServiceId", source = "value.proposerServiceId")
    @Mapping(target = "conclusion", source = "value.conclusion")
    @Mapping(target = "note", source = "value.note")
    @Mapping(target = "evidenceFileId", source = "value.evidence.fileId")
    @Mapping(target = "evidenceStorageId", source = "value.evidence.storageId")
    @Mapping(target = "evidenceSize", source = "value.evidence", qualifiedByName = "evidenceSize")
    @Mapping(target = "evidenceSha256", source = "value.evidence.sha256")
    @Mapping(target = "proposedAt", source = "value.proposedAt")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ReconciliationReviewProposalDO toDO(String tenant, ReconciliationReviewProposal value);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "differenceId", source = "value.reference.differenceId")
    @Mapping(target = "batchId", source = "value.reference.batchId")
    @Mapping(target = "runId", source = "value.reference.runId")
    @Mapping(target = "resultHash", source = "value.reference.resultHash")
    @Mapping(target = "matchKeyHash", source = "value.reference.matchKeyHash")
    @Mapping(target = "callerServiceId", source = "value.callerServiceId")
    @Mapping(target = "requestKey", source = "value.requestKey")
    @Mapping(target = "requestDigest", source = "value.requestDigest")
    @Mapping(target = "operation", source = "value.operation")
    @Mapping(target = "proposalId", source = "value.proposalId")
    @Mapping(target = "state", source = "value.state")
    @Mapping(target = "sourceVersion", source = "value.version")
    @Mapping(target = "note", source = "value.note")
    @Mapping(target = "occurredAt", source = "value.occurredAt")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ReconciliationReviewEventDO toDO(String tenant, ReconciliationReviewEvent value);

    @Named("evidenceSize")
    default Long evidenceSize(ReconciliationFileSnapshot value) {
        return value == null ? null : value.size();
    }
}
