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

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

@Table("reconciliation_review_event")
public class ReconciliationReviewEventDO extends BaseDO {
    private String differenceId;
    private String batchId;
    private String runId;
    private String resultHash;
    private String matchKeyHash;
    private String callerServiceId;
    private String requestKey;
    private String requestDigest;
    private String operation;
    private String proposalId;
    private String state;
    private Long sourceVersion;
    @DiagnosticHidden
    private String note;
    private java.time.LocalDateTime occurredAt;

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

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String value) {
        requestKey = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String value) {
        operation = value;
    }

    public String getProposalId() {
        return proposalId;
    }

    public void setProposalId(String value) {
        proposalId = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public Long getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(Long value) {
        sourceVersion = value;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String value) {
        note = value;
    }

    public java.time.LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(java.time.LocalDateTime value) {
        occurredAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
