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
import io.github.loadup.commons.json.*;
import java.time.LocalDateTime;

@Table("refund_order")
public class RefundOrderDO extends BaseDO {
    private LocalDateTime submissionStartedAt;
    public LocalDateTime getSubmissionStartedAt() { return submissionStartedAt; }
    public void setSubmissionStartedAt(LocalDateTime value) { submissionStartedAt = value; }

    private String refundRequestId;
    public String getRefundRequestId() { return refundRequestId; }
    public void setRefundRequestId(String value) { refundRequestId = value; }

    private String paymentId;
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }

    private String originalAttemptId;
    public String getOriginalAttemptId() { return originalAttemptId; }
    public void setOriginalAttemptId(String value) { originalAttemptId = value; }

    private String bindingRevisionId;
    public String getBindingRevisionId() { return bindingRevisionId; }
    public void setBindingRevisionId(String value) { bindingRevisionId = value; }

    private String channel;
    public String getChannel() { return channel; }
    public void setChannel(String value) { channel = value; }

    private String accountScopeHash;
    public String getAccountScopeHash() { return accountScopeHash; }
    public void setAccountScopeHash(String value) { accountScopeHash = value; }

    private String originalChannelRequestNo;
    public String getOriginalChannelRequestNo() { return originalChannelRequestNo; }
    public void setOriginalChannelRequestNo(String value) { originalChannelRequestNo = value; }

    private String originalChannelTransactionNo;
    public String getOriginalChannelTransactionNo() { return originalChannelTransactionNo; }
    public void setOriginalChannelTransactionNo(String value) { originalChannelTransactionNo = value; }

    private LocalDateTime originalSucceededAt;
    public LocalDateTime getOriginalSucceededAt() { return originalSucceededAt; }
    public void setOriginalSucceededAt(LocalDateTime value) { originalSucceededAt = value; }

    private String channelRefundNo;
    public String getChannelRefundNo() { return channelRefundNo; }
    public void setChannelRefundNo(String value) { channelRefundNo = value; }

    private String channelRefundTransactionNo;
    public String getChannelRefundTransactionNo() { return channelRefundTransactionNo; }
    public void setChannelRefundTransactionNo(String value) { channelRefundTransactionNo = value; }

    private Long amountMinor;
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private String terminalEvidenceId;
    public String getTerminalEvidenceId() { return terminalEvidenceId; }
    public void setTerminalEvidenceId(String value) { terminalEvidenceId = value; }

    private LocalDateTime nextQueryAt;
    public LocalDateTime getNextQueryAt() { return nextQueryAt; }
    public void setNextQueryAt(LocalDateTime value) { nextQueryAt = value; }

    private Long querySequence;
    public Long getQuerySequence() { return querySequence; }
    public void setQuerySequence(Long value) { querySequence = value; }

    private LocalDateTime succeededAt;
    public LocalDateTime getSucceededAt() { return succeededAt; }
    public void setSucceededAt(LocalDateTime value) { succeededAt = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
