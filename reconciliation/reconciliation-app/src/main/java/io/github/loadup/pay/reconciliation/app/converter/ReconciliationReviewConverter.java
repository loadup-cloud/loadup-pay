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
package io.github.loadup.pay.reconciliation.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.reconciliation.app.model.StoredReconciliationDifference;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewProposeCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationDifferenceDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewEventDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewProposalDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationSourceFileDTO;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReview;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewEvent;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewOperation;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewProposal;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationReviewConverter {
    @Mapping(target = "differenceId", source = "value.id")
    @Mapping(target = "batchId", source = "value.batchId")
    @Mapping(target = "runId", source = "value.runId")
    @Mapping(target = "resultHash", source = "resultHash")
    @Mapping(target = "matchKeyHash", expression = "java(value.difference().key().fingerprint())")
    ReconciliationReviewReference toReference(StoredReconciliationDifference value, String resultHash);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "reference", source = "reference")
    @Mapping(target = "proposerServiceId", source = "caller")
    @Mapping(target = "conclusion", source = "command.conclusion")
    @Mapping(target = "note", source = "command.note")
    @Mapping(target = "evidence", source = "evidence")
    @Mapping(target = "proposedAt", source = "at")
    ReconciliationReviewProposal toProposal(ReconciliationReviewReference reference, String id, String caller, ReconciliationReviewProposeCommand command, ReconciliationFileSnapshot evidence, LocalDateTime at);

    @Mapping(target = "id", source = "eventId")
    @Mapping(target = "reference", source = "head.reference")
    @Mapping(target = "callerServiceId", source = "caller")
    @Mapping(target = "requestKey", source = "requestKey")
    @Mapping(target = "requestDigest", source = "digest")
    @Mapping(target = "operation", source = "operation")
    @Mapping(target = "proposalId", source = "head.proposalId")
    @Mapping(target = "state", source = "head.state")
    @Mapping(target = "version", source = "head.version")
    @Mapping(target = "note", source = "note")
    @Mapping(target = "occurredAt", source = "at")
    ReconciliationReviewEvent toEvent(ReconciliationReview head, String eventId, String caller, String requestKey, String digest,
                                      ReconciliationReviewOperation operation, String note, LocalDateTime at);

    @Mapping(target = "proposalId", source = "id")
    @Mapping(target = "evidenceFileId", source = "evidence.fileId")
    @Mapping(target = "evidenceSha256", source = "evidence.sha256")
    @Mapping(target = "proposedAt", source = "proposedAt", qualifiedByName = "reviewOffset")
    ReconciliationReviewProposalDTO toDTO(ReconciliationReviewProposal value, @Context Clock clock);

    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "batchId", source = "reference.batchId")
    @Mapping(target = "differenceId", source = "reference.differenceId")
    @Mapping(target = "runId", source = "reference.runId")
    @Mapping(target = "occurredAt", source = "occurredAt", qualifiedByName = "reviewOffset")
    ReconciliationReviewEventDTO toDTO(ReconciliationReviewEvent value, @Context Clock clock);

    @Mapping(target = "batchId", source = "head.reference.batchId")
    @Mapping(target = "differenceId", source = "head.reference.differenceId")
    @Mapping(target = "runId", source = "head.reference.runId")
    @Mapping(target = "resultHash", source = "head.reference.resultHash")
    @Mapping(target = "matchKeyHash", source = "head.reference.matchKeyHash")
    @Mapping(target = "state", source = "head.state")
    @Mapping(target = "version", source = "head.version")
    @Mapping(target = "proposal", source = "proposal")
    @Mapping(target = "lastEvent", source = "lastEvent")
    @Mapping(target = "difference", source = "difference")
    ReconciliationReviewDTO toDTO(ReconciliationReview head, ReconciliationReviewProposalDTO proposal, ReconciliationReviewEventDTO lastEvent, ReconciliationDifferenceDTO difference);

    ReconciliationSourceFileDTO toDTO(ReconciliationFileSnapshot value);

    @Named("reviewOffset")
    default OffsetDateTime offset(LocalDateTime value, @Context Clock clock) {
        return value == null ? null : value.atZone(clock.getZone()).toOffsetDateTime();
    }
}
