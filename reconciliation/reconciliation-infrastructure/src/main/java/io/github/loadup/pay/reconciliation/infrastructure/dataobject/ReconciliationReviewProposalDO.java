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
package io.github.loadup.pay.reconciliation.infrastructure.dataobject;

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

public class ReconciliationReviewProposalDO extends BaseDO {
    private String differenceId;
    private String batchId;
    private String runId;
    private String resultHash;
    private String matchKeyHash;
    private String proposerServiceId;
    private String conclusion;
    @DiagnosticHidden
    private String note;
    private String evidenceFileId;
    @DiagnosticHidden
    private String evidenceStorageId;
    private Long evidenceSize;
    private String evidenceSha256;
    private java.time.LocalDateTime proposedAt;

    public String getDifferenceId() {
        return differenceId;
    }

    public void setDifferenceId(String value) {
        differenceId = value;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String value) {
        batchId = value;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String value) {
        runId = value;
    }

    public String getResultHash() {
        return resultHash;
    }

    public void setResultHash(String value) {
        resultHash = value;
    }

    public String getMatchKeyHash() {
        return matchKeyHash;
    }

    public void setMatchKeyHash(String value) {
        matchKeyHash = value;
    }

    public String getProposerServiceId() {
        return proposerServiceId;
    }

    public void setProposerServiceId(String value) {
        proposerServiceId = value;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String value) {
        conclusion = value;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String value) {
        note = value;
    }

    public String getEvidenceFileId() {
        return evidenceFileId;
    }

    public void setEvidenceFileId(String value) {
        evidenceFileId = value;
    }

    public String getEvidenceStorageId() {
        return evidenceStorageId;
    }

    public void setEvidenceStorageId(String value) {
        evidenceStorageId = value;
    }

    public Long getEvidenceSize() {
        return evidenceSize;
    }

    public void setEvidenceSize(Long value) {
        evidenceSize = value;
    }

    public String getEvidenceSha256() {
        return evidenceSha256;
    }

    public void setEvidenceSha256(String value) {
        evidenceSha256 = value;
    }

    public java.time.LocalDateTime getProposedAt() {
        return proposedAt;
    }

    public void setProposedAt(java.time.LocalDateTime value) {
        proposedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
