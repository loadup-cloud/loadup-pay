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

@Table("limit_reservation")
public class LimitReservationDO extends BaseDO {
    private String operationId;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String value) {
        operationId = value;
    }

    private String paymentId;

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    private String orderId;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    private String payeeMerchantId;

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    private Long amountMinor;

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long value) {
        amountMinor = value;
    }

    private String currency;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    private String policyId;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String value) {
        policyId = value;
    }

    private Integer policyVersion;

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer value) {
        policyVersion = value;
    }

    private String policyHash;

    public String getPolicyHash() {
        return policyHash;
    }

    public void setPolicyHash(String value) {
        policyHash = value;
    }

    private String requestDigest;

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    private String callerServiceId;

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    private LocalDateTime initiatedAt;

    public LocalDateTime getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(LocalDateTime value) {
        initiatedAt = value;
    }

    private String state;

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    private LocalDateTime reserveExpiresAt;

    public LocalDateTime getReserveExpiresAt() {
        return reserveExpiresAt;
    }

    public void setReserveExpiresAt(LocalDateTime value) {
        reserveExpiresAt = value;
    }

    private String activeAttemptId;

    public String getActiveAttemptId() {
        return activeAttemptId;
    }

    public void setActiveAttemptId(String value) {
        activeAttemptId = value;
    }

    private Long executionEpoch;

    public Long getExecutionEpoch() {
        return executionEpoch;
    }

    public void setExecutionEpoch(Long value) {
        executionEpoch = value;
    }

    private Long paymentSourceVersion;

    public Long getPaymentSourceVersion() {
        return paymentSourceVersion;
    }

    public void setPaymentSourceVersion(Long value) {
        paymentSourceVersion = value;
    }

    private Long rowVersion;

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
