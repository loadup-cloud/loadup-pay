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
import io.github.loadup.commons.json.ToStringAsJson;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("refund_transaction_claim")
public class RefundTransactionClaimDO extends BaseDO {
    private String refundId;
    private String paymentId;
    private String attemptId;
    private String channel;
    private String bindingRevisionId;
    private String accountScopeHash;
    private String channelRequestNo;
    private String originalChannelTransactionNo;
    private String channelRefundNo;
    private Long amountMinor;
    private String currency;
    private String channelRefundTransactionNo;
    private LocalDateTime succeededAt;
    private String receiptId;

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String value) {
        refundId = value;
    }

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

    public String getOriginalChannelTransactionNo() {
        return originalChannelTransactionNo;
    }

    public void setOriginalChannelTransactionNo(String value) {
        originalChannelTransactionNo = value;
    }

    public String getChannelRefundNo() {
        return channelRefundNo;
    }

    public void setChannelRefundNo(String value) {
        channelRefundNo = value;
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

    public String getChannelRefundTransactionNo() {
        return channelRefundTransactionNo;
    }

    public void setChannelRefundTransactionNo(String value) {
        channelRefundTransactionNo = value;
    }

    public LocalDateTime getSucceededAt() {
        return succeededAt;
    }

    public void setSucceededAt(LocalDateTime value) {
        succeededAt = value;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(String value) {
        receiptId = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
