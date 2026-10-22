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

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.*;
import java.time.LocalDateTime;

@Table("payment_attempt")
public class PaymentAttemptDO extends BaseDO {
    private String paymentId;
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }

    private Integer attemptNo;
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer value) { attemptNo = value; }

    private Long amountMinor;
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private String bindingRevisionId;
    public String getBindingRevisionId() { return bindingRevisionId; }
    public void setBindingRevisionId(String value) { bindingRevisionId = value; }

    private String channel;
    public String getChannel() { return channel; }
    public void setChannel(String value) { channel = value; }

    private String accessMode;
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String value) { accessMode = value; }

    private String accountScopeHash;
    public String getAccountScopeHash() { return accountScopeHash; }
    public void setAccountScopeHash(String value) { accountScopeHash = value; }

    private String method;
    public String getMethod() { return method; }
    public void setMethod(String value) { method = value; }

    private String channelRequestNo;
    public String getChannelRequestNo() { return channelRequestNo; }
    public void setChannelRequestNo(String value) { channelRequestNo = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private Long executionEpoch;
    public Long getExecutionEpoch() { return executionEpoch; }
    public void setExecutionEpoch(Long value) { executionEpoch = value; }

    private String noCollectionEvidenceId;
    public String getNoCollectionEvidenceId() { return noCollectionEvidenceId; }
    public void setNoCollectionEvidenceId(String value) { noCollectionEvidenceId = value; }

    private String limitOperationId;
    public String getLimitOperationId() { return limitOperationId; }
    public void setLimitOperationId(String value) { limitOperationId = value; }

    private String callerServiceId;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }

    private String requestKey;
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }

    private String requestDigest;
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }

    private String paymentRequestDigest;
    public String getPaymentRequestDigest() { return paymentRequestDigest; }
    public void setPaymentRequestDigest(String value) { paymentRequestDigest = value; }

    @DiagnosticHidden
    private String preparedInputRef;
    public String getPreparedInputRef() { return preparedInputRef; }
    public void setPreparedInputRef(String value) { preparedInputRef = value; }

    private String riskDecision;
    public String getRiskDecision() { return riskDecision; }
    public void setRiskDecision(String value) { riskDecision = value; }

    private String riskReason;
    public String getRiskReason() { return riskReason; }
    public void setRiskReason(String value) { riskReason = value; }

    @DiagnosticHidden
    private String riskConsultationRef;
    public String getRiskConsultationRef() { return riskConsultationRef; }
    public void setRiskConsultationRef(String value) { riskConsultationRef = value; }

    @DiagnosticHidden
    private String executionPermitHash;
    public String getExecutionPermitHash() { return executionPermitHash; }
    public void setExecutionPermitHash(String value) { executionPermitHash = value; }

    private LocalDateTime preparedAt;
    public LocalDateTime getPreparedAt() { return preparedAt; }
    public void setPreparedAt(LocalDateTime value) { preparedAt = value; }

    private LocalDateTime submissionStartedAt;
    public LocalDateTime getSubmissionStartedAt() { return submissionStartedAt; }
    public void setSubmissionStartedAt(LocalDateTime value) { submissionStartedAt = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
