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

@Table("refund_allocation_plan")
public class RefundAllocationPlanDO extends BaseDO {
    private String refundRequestId;
    public String getRefundRequestId() { return refundRequestId; }
    public void setRefundRequestId(String value) { refundRequestId = value; }

    private Integer planVersion;
    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer value) { planVersion = value; }

    private Integer sequenceNo;
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer value) { sequenceNo = value; }

    private String paymentId;
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }

    private String refundId;
    public String getRefundId() { return refundId; }
    public void setRefundId(String value) { refundId = value; }

    private Long amountMinor;
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
