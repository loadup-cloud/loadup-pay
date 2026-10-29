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

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

public class PaymentCloseEvidenceDO extends BaseDO {
    private String paymentId;
    private String attemptId;
    private String channel;
    private String bindingRevisionId;
    private String accountScopeHash;
    private String channelRequestNo;
    private Long amountMinor;
    private String currency;
    @DiagnosticHidden
    private String channelTransactionNo;
    private String receiptKey;
    private String sourceDigest;
    private Long executionEpoch;
    private java.time.LocalDateTime recordedAt;

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String value) {
        attemptId = value;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String value) {
        channel = value;
    }

    public String getBindingRevisionId() {
        return bindingRevisionId;
    }

    public void setBindingRevisionId(String value) {
        bindingRevisionId = value;
    }

    public String getAccountScopeHash() {
        return accountScopeHash;
    }

    public void setAccountScopeHash(String value) {
        accountScopeHash = value;
    }

    public String getChannelRequestNo() {
        return channelRequestNo;
    }

    public void setChannelRequestNo(String value) {
        channelRequestNo = value;
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

    public String getChannelTransactionNo() {
        return channelTransactionNo;
    }

    public void setChannelTransactionNo(String value) {
        channelTransactionNo = value;
    }

    public String getReceiptKey() {
        return receiptKey;
    }

    public void setReceiptKey(String value) {
        receiptKey = value;
    }

    public String getSourceDigest() {
        return sourceDigest;
    }

    public void setSourceDigest(String value) {
        sourceDigest = value;
    }

    public Long getExecutionEpoch() {
        return executionEpoch;
    }

    public void setExecutionEpoch(Long value) {
        executionEpoch = value;
    }

    public java.time.LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(java.time.LocalDateTime value) {
        recordedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
