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
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("refund_order")
public class RefundOrderDO extends BaseDO {
    private LocalDateTime submissionStartedAt;
    private String refundRequestId;
    private String paymentId;
    private String originalAttemptId;
    private String bindingRevisionId;
    private String channel;
    private String accountScopeHash;
    private String originalChannelRequestNo;
    private String originalChannelTransactionNo;
    private LocalDateTime originalSucceededAt;
    private String channelRefundNo;
    private String channelRefundTransactionNo;
    private Long amountMinor;
    private String currency;
    private String state;
    private LocalDateTime firstUnknownAt;
    private String terminalEvidenceId;
    private LocalDateTime nextQueryAt;
    private Long querySequence;
    private LocalDateTime succeededAt;
    private Long rowVersion;

    public LocalDateTime getSubmissionStartedAt() {
        return submissionStartedAt;
    }

    public void setSubmissionStartedAt(LocalDateTime value) {
        submissionStartedAt = value;
    }

    public String getRefundRequestId() {
        return refundRequestId;
    }

    public void setRefundRequestId(String value) {
        refundRequestId = value;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    public String getOriginalAttemptId() {
        return originalAttemptId;
    }

    public void setOriginalAttemptId(String value) {
        originalAttemptId = value;
    }

    public String getBindingRevisionId() {
        return bindingRevisionId;
    }

    public void setBindingRevisionId(String value) {
        bindingRevisionId = value;
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

    public String getOriginalChannelRequestNo() {
        return originalChannelRequestNo;
    }

    public void setOriginalChannelRequestNo(String value) {
        originalChannelRequestNo = value;
    }

    public String getOriginalChannelTransactionNo() {
        return originalChannelTransactionNo;
    }

    public void setOriginalChannelTransactionNo(String value) {
        originalChannelTransactionNo = value;
    }

    public LocalDateTime getOriginalSucceededAt() {
        return originalSucceededAt;
    }

    public void setOriginalSucceededAt(LocalDateTime value) {
        originalSucceededAt = value;
    }

    public String getChannelRefundNo() {
        return channelRefundNo;
    }

    public void setChannelRefundNo(String value) {
        channelRefundNo = value;
    }

    public String getChannelRefundTransactionNo() {
        return channelRefundTransactionNo;
    }

    public void setChannelRefundTransactionNo(String value) {
        channelRefundTransactionNo = value;
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

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public String getTerminalEvidenceId() {
        return terminalEvidenceId;
    }

    public LocalDateTime getFirstUnknownAt() {
        return firstUnknownAt;
    }

    public void setFirstUnknownAt(LocalDateTime value) {
        firstUnknownAt = value;
    }

    public void setTerminalEvidenceId(String value) {
        terminalEvidenceId = value;
    }

    public LocalDateTime getNextQueryAt() {
        return nextQueryAt;
    }

    public void setNextQueryAt(LocalDateTime value) {
        nextQueryAt = value;
    }

    public Long getQuerySequence() {
        return querySequence;
    }

    public void setQuerySequence(Long value) {
        querySequence = value;
    }

    public LocalDateTime getSucceededAt() {
        return succeededAt;
    }

    public void setSucceededAt(LocalDateTime value) {
        succeededAt = value;
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
