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
import io.github.loadup.commons.json.ToStringAsJson;
import java.time.LocalDateTime;

@Table("order_payment_outcome")
public class OrderPaymentOutcomeDO extends BaseDO {
    private String allocationId;
    public String getAllocationId() { return allocationId; }
    public void setAllocationId(String value) { allocationId = value; }

    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String paymentId;
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }

    private Long amountMinor;
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private String outcomeKind;
    public String getOutcomeKind() { return outcomeKind; }
    public void setOutcomeKind(String value) { outcomeKind = value; }

    private Long paymentSourceVersion;
    public Long getPaymentSourceVersion() { return paymentSourceVersion; }
    public void setPaymentSourceVersion(Long value) { paymentSourceVersion = value; }

    private String evidenceId;
    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String value) { evidenceId = value; }

    private Boolean executionFenced;
    public Boolean getExecutionFenced() { return executionFenced; }
    public void setExecutionFenced(Boolean value) { executionFenced = value; }

    private LocalDateTime observedAt;
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime value) { observedAt = value; }

    @Override
    public String toString() { return ToStringAsJson.reflectionToString(this); }
}
