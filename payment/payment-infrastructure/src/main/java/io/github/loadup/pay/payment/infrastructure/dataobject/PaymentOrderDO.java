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

@Table("payment_order")
public class PaymentOrderDO extends BaseDO {
    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String allocationId;
    public String getAllocationId() { return allocationId; }
    public void setAllocationId(String value) { allocationId = value; }

    private String admissionId;
    public String getAdmissionId() { return admissionId; }
    public void setAdmissionId(String value) { admissionId = value; }

    private String payeeMerchantId;
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }

    private Long amountMinor;
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private String requestDigest;
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }

    private String callerServiceId;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private String activeAttemptId;
    public String getActiveAttemptId() { return activeAttemptId; }
    public void setActiveAttemptId(String value) { activeAttemptId = value; }

    private String successfulAttemptId;
    public String getSuccessfulAttemptId() { return successfulAttemptId; }
    public void setSuccessfulAttemptId(String value) { successfulAttemptId = value; }

    private String limitOperationId;
    public String getLimitOperationId() { return limitOperationId; }
    public void setLimitOperationId(String value) { limitOperationId = value; }

    private LocalDateTime initiatedAt;
    public LocalDateTime getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(LocalDateTime value) { initiatedAt = value; }

    private LocalDateTime succeededAt;
    public LocalDateTime getSucceededAt() { return succeededAt; }
    public void setSucceededAt(LocalDateTime value) { succeededAt = value; }

    private String terminalEvidenceId;
    public String getTerminalEvidenceId() { return terminalEvidenceId; }
    public void setTerminalEvidenceId(String value) { terminalEvidenceId = value; }

    @DiagnosticHidden
    private String intentPayload;
    public String getIntentPayload() { return intentPayload; }
    public void setIntentPayload(String value) { intentPayload = value; }

    private String payloadHash;
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String value) { payloadHash = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
