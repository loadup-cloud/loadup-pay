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
import java.time.LocalDateTime;

@Table("acquire_order")
public class AcquireOrderDO extends BaseDO {
    private String payeeMerchantId;

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    private String merchantOrderNo;

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String value) {
        merchantOrderNo = value;
    }

    private String admissionId;

    public String getAdmissionId() {
        return admissionId;
    }

    public void setAdmissionId(String value) {
        admissionId = value;
    }

    private String contractSnapshotId;

    public String getContractSnapshotId() {
        return contractSnapshotId;
    }

    public void setContractSnapshotId(String value) {
        contractSnapshotId = value;
    }

    private String subject;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String value) {
        subject = value;
    }

    private String currency;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    private Long amountMinor;

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long value) {
        amountMinor = value;
    }

    private Long paidAmountMinor;

    public Long getPaidAmountMinor() {
        return paidAmountMinor;
    }

    public void setPaidAmountMinor(Long value) {
        paidAmountMinor = value;
    }

    private Long paymentReservedMinor;

    public Long getPaymentReservedMinor() {
        return paymentReservedMinor;
    }

    public void setPaymentReservedMinor(Long value) {
        paymentReservedMinor = value;
    }

    private Integer successfulPaymentCount;

    public Integer getSuccessfulPaymentCount() {
        return successfulPaymentCount;
    }

    public void setSuccessfulPaymentCount(Integer value) {
        successfulPaymentCount = value;
    }

    private Integer reservedPaymentCount;

    public Integer getReservedPaymentCount() {
        return reservedPaymentCount;
    }

    public void setReservedPaymentCount(Integer value) {
        reservedPaymentCount = value;
    }

    private String state;

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    private LocalDateTime admittedAt;

    public LocalDateTime getAdmittedAt() {
        return admittedAt;
    }

    public void setAdmittedAt(LocalDateTime value) {
        admittedAt = value;
    }

    private LocalDateTime orderDueAt;

    public LocalDateTime getOrderDueAt() {
        return orderDueAt;
    }

    public void setOrderDueAt(LocalDateTime value) {
        orderDueAt = value;
    }

    private Long deadlineVersion;

    public Long getDeadlineVersion() {
        return deadlineVersion;
    }

    public void setDeadlineVersion(Long value) {
        deadlineVersion = value;
    }

    private Long closeEpoch;

    public Long getCloseEpoch() {
        return closeEpoch;
    }

    public void setCloseEpoch(Long value) {
        closeEpoch = value;
    }

    private LocalDateTime paidAt;

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime value) {
        paidAt = value;
    }

    private LocalDateTime closedAt;

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime value) {
        closedAt = value;
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
