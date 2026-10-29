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
package io.github.loadup.pay.payment.infrastructure.dataobject;

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

public class PaymentAttemptDO extends BaseDO {
    private String paymentId;
    private Integer attemptNo;
    private Long amountMinor;
    private String currency;
    private String bindingRevisionId;
    private String channel;
    private String accessMode;
    private String accountScopeHash;
    private String method;
    private String channelRequestNo;
    private String state;
    private Long executionEpoch;
    private String noCollectionEvidenceId;
    private String limitOperationId;
    private String callerServiceId;
    private String requestKey;
    private String requestDigest;
    private String paymentRequestDigest;
    @DiagnosticHidden
    private String preparedInputRef;
    private String riskDecision;
    private String riskReason;
    @DiagnosticHidden
    private String riskConsultationRef;
    @DiagnosticHidden
    private String executionPermitHash;
    private LocalDateTime preparedAt;
    private LocalDateTime submissionStartedAt;
    private Long rowVersion;

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer value) {
        attemptNo = value;
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

    public String getBindingRevisionId() {
        return bindingRevisionId;
    }

    public void setBindingRevisionId(String value) {
        bindingRevisionId = value;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String value) {
        channel = value;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String value) {
        accessMode = value;
    }

    public String getAccountScopeHash() {
        return accountScopeHash;
    }

    public void setAccountScopeHash(String value) {
        accountScopeHash = value;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String value) {
        method = value;
    }

    public String getChannelRequestNo() {
        return channelRequestNo;
    }

    public void setChannelRequestNo(String value) {
        channelRequestNo = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public Long getExecutionEpoch() {
        return executionEpoch;
    }

    public void setExecutionEpoch(Long value) {
        executionEpoch = value;
    }

    public String getNoCollectionEvidenceId() {
        return noCollectionEvidenceId;
    }

    public void setNoCollectionEvidenceId(String value) {
        noCollectionEvidenceId = value;
    }

    public String getLimitOperationId() {
        return limitOperationId;
    }

    public void setLimitOperationId(String value) {
        limitOperationId = value;
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

    public String getPaymentRequestDigest() {
        return paymentRequestDigest;
    }

    public void setPaymentRequestDigest(String value) {
        paymentRequestDigest = value;
    }

    public String getPreparedInputRef() {
        return preparedInputRef;
    }

    public void setPreparedInputRef(String value) {
        preparedInputRef = value;
    }

    public String getRiskDecision() {
        return riskDecision;
    }

    public void setRiskDecision(String value) {
        riskDecision = value;
    }

    public String getRiskReason() {
        return riskReason;
    }

    public void setRiskReason(String value) {
        riskReason = value;
    }

    public String getRiskConsultationRef() {
        return riskConsultationRef;
    }

    public void setRiskConsultationRef(String value) {
        riskConsultationRef = value;
    }

    public String getExecutionPermitHash() {
        return executionPermitHash;
    }

    public void setExecutionPermitHash(String value) {
        executionPermitHash = value;
    }

    public LocalDateTime getPreparedAt() {
        return preparedAt;
    }

    public void setPreparedAt(LocalDateTime value) {
        preparedAt = value;
    }

    public LocalDateTime getSubmissionStartedAt() {
        return submissionStartedAt;
    }

    public void setSubmissionStartedAt(LocalDateTime value) {
        submissionStartedAt = value;
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
