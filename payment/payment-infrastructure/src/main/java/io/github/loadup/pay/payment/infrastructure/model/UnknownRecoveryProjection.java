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
package io.github.loadup.pay.payment.infrastructure.model;

import io.github.loadup.commons.json.ToStringAsJson;
import java.time.LocalDateTime;

/** Non-table projection for the bounded operational keyset query. */
public class UnknownRecoveryProjection {
    private String recordId;
    private String subjectId;
    private String orderId;
    private String payeeMerchantId;
    private Long amountMinor;
    private String currency;
    private LocalDateTime firstUnknownAt;
    private Long rowVersion;
    private Long recoveryGeneration;
    private Boolean recoveryCompleted;
    public String getRecordId() { return recordId; }
    public void setRecordId(String value) { recordId = value; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String value) { subjectId = value; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public LocalDateTime getFirstUnknownAt() { return firstUnknownAt; }
    public void setFirstUnknownAt(LocalDateTime value) { firstUnknownAt = value; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }
    public Long getRecoveryGeneration() { return recoveryGeneration; }
    public void setRecoveryGeneration(Long value) { recoveryGeneration = value; }
    public Boolean getRecoveryCompleted() { return recoveryCompleted; }
    public void setRecoveryCompleted(Boolean value) { recoveryCompleted = value; }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
