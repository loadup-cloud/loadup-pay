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
package io.github.loadup.pay.acquire.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.*;

@Table("fee_adjustment")
public class FeeAdjustmentDO extends BaseDO {
    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String feeId;
    public String getFeeId() { return feeId; }
    public void setFeeId(String value) { feeId = value; }

    private String triggerKey;
    public String getTriggerKey() { return triggerKey; }
    public void setTriggerKey(String value) { triggerKey = value; }

    private String sourceEventId;
    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String value) { sourceEventId = value; }

    private Long sourceVersion;
    public Long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Long value) { sourceVersion = value; }

    private Long adjustmentMinor;
    public Long getAdjustmentMinor() { return adjustmentMinor; }
    public void setAdjustmentMinor(Long value) { adjustmentMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private Long cumulativeRefundedMinor;
    public Long getCumulativeRefundedMinor() { return cumulativeRefundedMinor; }
    public void setCumulativeRefundedMinor(Long value) { cumulativeRefundedMinor = value; }

    @DiagnosticHidden private String ruleSnapshot;
    public String getRuleSnapshot() { return ruleSnapshot; }
    public void setRuleSnapshot(String value) { ruleSnapshot = value; }

    private java.time.LocalDateTime appliedAt;
    public java.time.LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(java.time.LocalDateTime value) { appliedAt = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
