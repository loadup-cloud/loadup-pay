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

public class ReconciliationVerificationDO extends BaseDO {
    private String differenceId;
    private String batchId;
    private String runId;
    private String resultHash;
    private String matchKeyHash;
    private String proposalId;
    private String approvalEventId;
    private Long reviewVersion;
    private String callerServiceId;
    private String requestKey;
    private String requestDigest;
    private String channel;
    private String accountScopeHash;
    private java.time.LocalDate billDate;
    private String statementProfile;
    private String kind;
    @DiagnosticHidden
    private String channelTransactionNo;
    @DiagnosticHidden
    private String channelRefundNo;
    @DiagnosticHidden
    private String merchantPaymentNo;
    private String deliveryId;
    private String state;
    private Long rowVersion;
    private Long acceptedEpochMicros;
    private String disposition;
    private String bindingRevisionId;
    private String paymentId;
    private String attemptId;
    private String refundId;
    private Long sourceVersion;
    private String localState;
    private String outcome;
    private Long amountMinor;
    private String currency;
    @DiagnosticHidden
    private String observedChannelTransactionNo;
    @DiagnosticHidden
    private String observedRefundTransactionNo;
    private Long succeededEpochMicros;
    private String sourceDigest;
    private Long startedEpochMicros;
    private Long finishedEpochMicros;

    public Long getAcceptedEpochMicros() {
        return acceptedEpochMicros;
    }

    public void setAcceptedEpochMicros(Long value) {
        acceptedEpochMicros = value;
    }

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

    public String getProposalId() {
        return proposalId;
    }

    public void setProposalId(String value) {
        proposalId = value;
    }

    public String getApprovalEventId() {
        return approvalEventId;
    }

    public void setApprovalEventId(String value) {
        approvalEventId = value;
    }

    public Long getReviewVersion() {
        return reviewVersion;
    }

    public void setReviewVersion(Long value) {
        reviewVersion = value;
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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String value) {
        channel = value;
    }

    public String getAccountScopeHash() {
        return accountScopeHash;
    }

    public void setAccountScopeHash(String value) {
        accountScopeHash = value;
    }

    public java.time.LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(java.time.LocalDate value) {
        billDate = value;
    }

    public String getStatementProfile() {
        return statementProfile;
    }

    public void setStatementProfile(String value) {
        statementProfile = value;
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

    public String getMerchantPaymentNo() {
        return merchantPaymentNo;
    }

    public void setMerchantPaymentNo(String value) {
        merchantPaymentNo = value;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String value) {
        deliveryId = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String value) {
        disposition = value;
    }

    public String getBindingRevisionId() {
        return bindingRevisionId;
    }

    public void setBindingRevisionId(String value) {
        bindingRevisionId = value;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String value) {
        attemptId = value;
    }

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String value) {
        refundId = value;
    }

    public Long getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(Long value) {
        sourceVersion = value;
    }

    public String getLocalState() {
        return localState;
    }

    public void setLocalState(String value) {
        localState = value;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String value) {
        outcome = value;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long value) {
        amountMinor = value;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    public String getObservedChannelTransactionNo() {
        return observedChannelTransactionNo;
    }

    public void setObservedChannelTransactionNo(String value) {
        observedChannelTransactionNo = value;
    }

    public String getObservedRefundTransactionNo() {
        return observedRefundTransactionNo;
    }

    public void setObservedRefundTransactionNo(String value) {
        observedRefundTransactionNo = value;
    }

    public Long getSucceededEpochMicros() {
        return succeededEpochMicros;
    }

    public void setSucceededEpochMicros(Long value) {
        succeededEpochMicros = value;
    }

    public String getSourceDigest() {
        return sourceDigest;
    }

    public void setSourceDigest(String value) {
        sourceDigest = value;
    }

    public Long getStartedEpochMicros() {
        return startedEpochMicros;
    }

    public void setStartedEpochMicros(Long value) {
        startedEpochMicros = value;
    }

    public Long getFinishedEpochMicros() {
        return finishedEpochMicros;
    }

    public void setFinishedEpochMicros(Long value) {
        finishedEpochMicros = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
