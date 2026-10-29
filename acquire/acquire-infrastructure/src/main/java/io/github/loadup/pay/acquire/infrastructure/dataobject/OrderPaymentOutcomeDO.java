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

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

public class OrderPaymentOutcomeDO extends BaseDO {
    private String allocationId;
    private String orderId;
    private String paymentId;
    private Long amountMinor;
    private String currency;
    private String outcomeKind;
    private Long paymentSourceVersion;
    private String evidenceId;
    private Boolean executionFenced;
    private LocalDateTime observedAt;

    public String getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(String value) {
        allocationId = value;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
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

    public String getOutcomeKind() {
        return outcomeKind;
    }

    public void setOutcomeKind(String value) {
        outcomeKind = value;
    }

    public Long getPaymentSourceVersion() {
        return paymentSourceVersion;
    }

    public void setPaymentSourceVersion(Long value) {
        paymentSourceVersion = value;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String value) {
        evidenceId = value;
    }

    public Boolean getExecutionFenced() {
        return executionFenced;
    }

    public void setExecutionFenced(Boolean value) {
        executionFenced = value;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(LocalDateTime value) {
        observedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
