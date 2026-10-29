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
import io.github.loadup.commons.json.ToStringAsJson;

public class ReconciliationReviewDO extends BaseDO {
    private String batchId;
    private String runId;
    private String resultHash;
    private String matchKeyHash;
    private String state;
    private String proposalId;
    private String lastEventId;
    private Long rowVersion;

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

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public String getProposalId() {
        return proposalId;
    }

    public void setProposalId(String value) {
        proposalId = value;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String value) {
        lastEventId = value;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
