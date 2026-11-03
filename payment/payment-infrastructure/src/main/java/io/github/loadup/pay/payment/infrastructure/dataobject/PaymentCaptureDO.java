/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import io.github.loadup.commons.json.ToStringAsJson;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("payment_reconciliation_capture")
public class PaymentCaptureDO extends BaseDO {
    private String callerServiceId;
    private String requestKey;
    private String requestDigest;
    private String channel;
    private String accountScopeHash;
    private LocalDate billDate;
    private String statementProfile;
    private String systemZone;
    private String state;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long paymentCount;
    private Long paymentAmountMinor;
    private Long refundCount;
    private Long refundAmountMinor;
    private String contentHash;
    private String blocker;
    private String blockerSubjectId;
    private Long rowVersion;

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String value) {
        requestKey = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String value) {
        channel = value;
    }

    public String getAccountScopeHash() {
        return accountScopeHash;
    }

    public void setAccountScopeHash(String value) {
        accountScopeHash = value;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDate value) {
        billDate = value;
    }

    public String getStatementProfile() {
        return statementProfile;
    }

    public void setStatementProfile(String value) {
        statementProfile = value;
    }

    public String getSystemZone() {
        return systemZone;
    }

    public void setSystemZone(String value) {
        systemZone = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime value) {
        startedAt = value;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime value) {
        finishedAt = value;
    }

    public Long getPaymentCount() {
        return paymentCount;
    }

    public void setPaymentCount(Long value) {
        paymentCount = value;
    }

    public Long getPaymentAmountMinor() {
        return paymentAmountMinor;
    }

    public void setPaymentAmountMinor(Long value) {
        paymentAmountMinor = value;
    }

    public Long getRefundCount() {
        return refundCount;
    }

    public void setRefundCount(Long value) {
        refundCount = value;
    }

    public Long getRefundAmountMinor() {
        return refundAmountMinor;
    }

    public void setRefundAmountMinor(Long value) {
        refundAmountMinor = value;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String value) {
        contentHash = value;
    }

    public String getBlocker() {
        return blocker;
    }

    public void setBlocker(String value) {
        blocker = value;
    }

    public String getBlockerSubjectId() {
        return blockerSubjectId;
    }

    public void setBlockerSubjectId(String value) {
        blockerSubjectId = value;
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
