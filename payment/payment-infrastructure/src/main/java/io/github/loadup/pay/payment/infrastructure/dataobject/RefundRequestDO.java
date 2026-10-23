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

@Table("refund_request")
public class RefundRequestDO extends BaseDO {
    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String payeeMerchantId;
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }

    private String merchantRefundNo;
    public String getMerchantRefundNo() { return merchantRefundNo; }
    public void setMerchantRefundNo(String value) { merchantRefundNo = value; }

    private String reasonCode;
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }

    private String requestDigest;
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }

    private String callerSubjectHash;
    public String getCallerSubjectHash() { return callerSubjectHash; }
    public void setCallerSubjectHash(String value) { callerSubjectHash = value; }

    private String admissionId;
    public String getAdmissionId() { return admissionId; }
    public void setAdmissionId(String value) { admissionId = value; }

    private Long amountMinor;
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private String allocationPolicy;
    public String getAllocationPolicy() { return allocationPolicy; }
    public void setAllocationPolicy(String value) { allocationPolicy = value; }

    private Integer planVersion;
    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer value) { planVersion = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private Long succeededMinor;
    public Long getSucceededMinor() { return succeededMinor; }
    public void setSucceededMinor(Long value) { succeededMinor = value; }

    private Long failedMinor;
    public Long getFailedMinor() { return failedMinor; }
    public void setFailedMinor(Long value) { failedMinor = value; }

    private Integer unknownCount;
    public Integer getUnknownCount() { return unknownCount; }
    public void setUnknownCount(Integer value) { unknownCount = value; }

    @DiagnosticHidden private String refundTerms;
    public String getRefundTerms() { return refundTerms; }
    public void setRefundTerms(String value) { refundTerms = value; }

    private String termsHash;
    public String getTermsHash() { return termsHash; }
    public void setTermsHash(String value) { termsHash = value; }

    private LocalDateTime plannedAt;
    public LocalDateTime getPlannedAt() { return plannedAt; }
    public void setPlannedAt(LocalDateTime value) { plannedAt = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
