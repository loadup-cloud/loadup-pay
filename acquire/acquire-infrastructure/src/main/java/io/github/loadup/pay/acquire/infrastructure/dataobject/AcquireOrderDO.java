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

public class AcquireOrderDO extends BaseDO {
    private String payeeMerchantId;
    private String merchantOrderNo;
    private String admissionId;
    private String contractSnapshotId;
    private String subject;
    private String currency;
    private Long amountMinor;
    private Long paidAmountMinor;
    private Long paymentReservedMinor;
    private Integer successfulPaymentCount;
    private Integer reservedPaymentCount;
    private String state;
    private LocalDateTime admittedAt;
    private LocalDateTime orderDueAt;
    private Long deadlineVersion;
    private Long closeEpoch;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private Long rowVersion;

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String value) {
        merchantOrderNo = value;
    }

    public String getAdmissionId() {
        return admissionId;
    }

    public void setAdmissionId(String value) {
        admissionId = value;
    }

    public String getContractSnapshotId() {
        return contractSnapshotId;
    }

    public void setContractSnapshotId(String value) {
        contractSnapshotId = value;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String value) {
        subject = value;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long value) {
        amountMinor = value;
    }

    public Long getPaidAmountMinor() {
        return paidAmountMinor;
    }

    public void setPaidAmountMinor(Long value) {
        paidAmountMinor = value;
    }

    public Long getPaymentReservedMinor() {
        return paymentReservedMinor;
    }

    public void setPaymentReservedMinor(Long value) {
        paymentReservedMinor = value;
    }

    public Integer getSuccessfulPaymentCount() {
        return successfulPaymentCount;
    }

    public void setSuccessfulPaymentCount(Integer value) {
        successfulPaymentCount = value;
    }

    public Integer getReservedPaymentCount() {
        return reservedPaymentCount;
    }

    public void setReservedPaymentCount(Integer value) {
        reservedPaymentCount = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public LocalDateTime getAdmittedAt() {
        return admittedAt;
    }

    public void setAdmittedAt(LocalDateTime value) {
        admittedAt = value;
    }

    public LocalDateTime getOrderDueAt() {
        return orderDueAt;
    }

    public void setOrderDueAt(LocalDateTime value) {
        orderDueAt = value;
    }

    public Long getDeadlineVersion() {
        return deadlineVersion;
    }

    public void setDeadlineVersion(Long value) {
        deadlineVersion = value;
    }

    public Long getCloseEpoch() {
        return closeEpoch;
    }

    public void setCloseEpoch(Long value) {
        closeEpoch = value;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime value) {
        paidAt = value;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime value) {
        closedAt = value;
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
