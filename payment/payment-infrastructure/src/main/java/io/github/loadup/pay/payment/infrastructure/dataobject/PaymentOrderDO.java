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
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("payment_order")
public class PaymentOrderDO extends BaseDO {
    private String orderId;
    private String allocationId;
    private String admissionId;
    private String payeeMerchantId;
    private Long amountMinor;
    private String currency;
    private String requestDigest;
    private String callerServiceId;
    private String state;
    private String activeAttemptId;
    private String successfulAttemptId;
    private String limitOperationId;
    private LocalDateTime initiatedAt;
    private LocalDateTime firstUnknownAt;
    private LocalDateTime succeededAt;
    private String terminalEvidenceId;
    @DiagnosticHidden
    private String intentPayload;
    private String payloadHash;
    private Long rowVersion;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(String value) {
        allocationId = value;
    }

    public String getAdmissionId() {
        return admissionId;
    }

    public void setAdmissionId(String value) {
        admissionId = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
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

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public String getActiveAttemptId() {
        return activeAttemptId;
    }

    public void setActiveAttemptId(String value) {
        activeAttemptId = value;
    }

    public String getSuccessfulAttemptId() {
        return successfulAttemptId;
    }

    public void setSuccessfulAttemptId(String value) {
        successfulAttemptId = value;
    }

    public String getLimitOperationId() {
        return limitOperationId;
    }

    public void setLimitOperationId(String value) {
        limitOperationId = value;
    }

    public LocalDateTime getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(LocalDateTime value) {
        initiatedAt = value;
    }

    public LocalDateTime getSucceededAt() {
        return succeededAt;
    }

    public void setSucceededAt(LocalDateTime value) {
        succeededAt = value;
    }

    public LocalDateTime getFirstUnknownAt() {
        return firstUnknownAt;
    }

    public void setFirstUnknownAt(LocalDateTime value) {
        firstUnknownAt = value;
    }

    public String getTerminalEvidenceId() {
        return terminalEvidenceId;
    }

    public void setTerminalEvidenceId(String value) {
        terminalEvidenceId = value;
    }

    public String getIntentPayload() {
        return intentPayload;
    }

    public void setIntentPayload(String value) {
        intentPayload = value;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String value) {
        payloadHash = value;
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
