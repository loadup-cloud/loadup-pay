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

public class ReconciliationDifferenceDO extends BaseDO {
    private String batchId;
    private String runId;
    private String matchKeyHash;
    private String kind;
    @io.github.loadup.commons.json.DiagnosticHidden
    private String channelTransactionNo;
    @io.github.loadup.commons.json.DiagnosticHidden
    private String channelRefundNo;
    private String channelEntryId;
    private String ledgerEntryId;
    private Boolean channelOnly;
    private Boolean ledgerOnly;
    private Boolean amountMismatch;
    private Boolean requestMismatch;
    private Boolean stateMismatch;

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

    public String getMatchKeyHash() {
        return matchKeyHash;
    }

    public void setMatchKeyHash(String value) {
        matchKeyHash = value;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String value) {
        kind = value;
    }

    public String getChannelTransactionNo() {
        return channelTransactionNo;
    }

    public void setChannelTransactionNo(String value) {
        channelTransactionNo = value;
    }

    public String getChannelRefundNo() {
        return channelRefundNo;
    }

    public void setChannelRefundNo(String value) {
        channelRefundNo = value;
    }

    public String getChannelEntryId() {
        return channelEntryId;
    }

    public void setChannelEntryId(String value) {
        channelEntryId = value;
    }

    public String getLedgerEntryId() {
        return ledgerEntryId;
    }

    public void setLedgerEntryId(String value) {
        ledgerEntryId = value;
    }

    public Boolean getChannelOnly() {
        return channelOnly;
    }

    public void setChannelOnly(Boolean value) {
        channelOnly = value;
    }

    public Boolean getLedgerOnly() {
        return ledgerOnly;
    }

    public void setLedgerOnly(Boolean value) {
        ledgerOnly = value;
    }

    public Boolean getAmountMismatch() {
        return amountMismatch;
    }

    public void setAmountMismatch(Boolean value) {
        amountMismatch = value;
    }

    public Boolean getRequestMismatch() {
        return requestMismatch;
    }

    public void setRequestMismatch(Boolean value) {
        requestMismatch = value;
    }

    public Boolean getStateMismatch() {
        return stateMismatch;
    }

    public void setStateMismatch(Boolean value) {
        stateMismatch = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
