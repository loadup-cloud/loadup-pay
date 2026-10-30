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
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("refund_request")
public class RefundRequestDO extends BaseDO {
    private String orderId;
    private String payeeMerchantId;
    private String merchantRefundNo;
    private String reasonCode;
    private String requestDigest;
    private String callerSubjectHash;
    private String admissionId;
    private Long amountMinor;
    private String currency;
    private String allocationPolicy;
    private Integer planVersion;
    private String state;
    private Long succeededMinor;
    private Long failedMinor;
    private Integer unknownCount;
    @DiagnosticHidden
    private String refundTerms;
    private String termsHash;
    private LocalDateTime plannedAt;
    private Long rowVersion;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getMerchantRefundNo() {
        return merchantRefundNo;
    }

    public void setMerchantRefundNo(String value) {
        merchantRefundNo = value;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String value) {
        reasonCode = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getCallerSubjectHash() {
        return callerSubjectHash;
    }

    public void setCallerSubjectHash(String value) {
        callerSubjectHash = value;
    }

    public String getAdmissionId() {
        return admissionId;
    }

    public void setAdmissionId(String value) {
        admissionId = value;
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

    public String getAllocationPolicy() {
        return allocationPolicy;
    }

    public void setAllocationPolicy(String value) {
        allocationPolicy = value;
    }

    public Integer getPlanVersion() {
        return planVersion;
    }

    public void setPlanVersion(Integer value) {
        planVersion = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public Long getSucceededMinor() {
        return succeededMinor;
    }

    public void setSucceededMinor(Long value) {
        succeededMinor = value;
    }

    public Long getFailedMinor() {
        return failedMinor;
    }

    public void setFailedMinor(Long value) {
        failedMinor = value;
    }

    public Integer getUnknownCount() {
        return unknownCount;
    }

    public void setUnknownCount(Integer value) {
        unknownCount = value;
    }

    public String getRefundTerms() {
        return refundTerms;
    }

    public void setRefundTerms(String value) {
        refundTerms = value;
    }

    public String getTermsHash() {
        return termsHash;
    }

    public void setTermsHash(String value) {
        termsHash = value;
    }

    public LocalDateTime getPlannedAt() {
        return plannedAt;
    }

    public void setPlannedAt(LocalDateTime value) {
        plannedAt = value;
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
