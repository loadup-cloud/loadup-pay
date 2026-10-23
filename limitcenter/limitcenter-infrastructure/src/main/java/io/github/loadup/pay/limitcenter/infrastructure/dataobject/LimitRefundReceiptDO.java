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
package io.github.loadup.pay.limitcenter.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;
import java.time.LocalDateTime;

@Table("limit_refund_receipt")
public class LimitRefundReceiptDO extends BaseDO {
    private String callerServiceId;
    private String operationId;
    private String paymentId;
    private String refundId;
    private Long refundVersion;
    private String businessEventId;
    private String refundEvidenceId;
    private String requestDigest;
    private Long cumulativePaymentRefundedMinor;
    private Long verifiedPaymentRefundedMinor;
    private Long verifiedPaymentRefundVersion;
    private String result;
    private Long restoredAmountMinor;
    private Boolean restoredPaymentCount;
    private LocalDateTime acceptedAt;

    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String value) { operationId = value; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }
    public String getRefundId() { return refundId; }
    public void setRefundId(String value) { refundId = value; }
    public Long getRefundVersion() { return refundVersion; }
    public void setRefundVersion(Long value) { refundVersion = value; }
    public String getBusinessEventId() { return businessEventId; }
    public void setBusinessEventId(String value) { businessEventId = value; }
    public String getRefundEvidenceId() { return refundEvidenceId; }
    public void setRefundEvidenceId(String value) { refundEvidenceId = value; }
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }
    public Long getCumulativePaymentRefundedMinor() { return cumulativePaymentRefundedMinor; }
    public void setCumulativePaymentRefundedMinor(Long value) { cumulativePaymentRefundedMinor = value; }
    public Long getVerifiedPaymentRefundedMinor() { return verifiedPaymentRefundedMinor; }
    public void setVerifiedPaymentRefundedMinor(Long value) { verifiedPaymentRefundedMinor = value; }
    public Long getVerifiedPaymentRefundVersion() { return verifiedPaymentRefundVersion; }
    public void setVerifiedPaymentRefundVersion(Long value) { verifiedPaymentRefundVersion = value; }
    public String getResult() { return result; }
    public void setResult(String value) { result = value; }
    public Long getRestoredAmountMinor() { return restoredAmountMinor; }
    public void setRestoredAmountMinor(Long value) { restoredAmountMinor = value; }
    public Boolean getRestoredPaymentCount() { return restoredPaymentCount; }
    public void setRestoredPaymentCount(Boolean value) { restoredPaymentCount = value; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime value) { acceptedAt = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
