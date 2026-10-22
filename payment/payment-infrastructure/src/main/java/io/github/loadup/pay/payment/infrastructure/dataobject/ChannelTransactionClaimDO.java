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

@Table("channel_transaction_claim")
public class ChannelTransactionClaimDO extends BaseDO {
    private String paymentId;
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }

    private String attemptId;
    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String value) { attemptId = value; }

    private String channel;
    public String getChannel() { return channel; }
    public void setChannel(String value) { channel = value; }

    private String bindingRevisionId;
    public String getBindingRevisionId() { return bindingRevisionId; }
    public void setBindingRevisionId(String value) { bindingRevisionId = value; }

    private String accountScopeHash;
    public String getAccountScopeHash() { return accountScopeHash; }
    public void setAccountScopeHash(String value) { accountScopeHash = value; }

    private String channelRequestNo;
    public String getChannelRequestNo() { return channelRequestNo; }
    public void setChannelRequestNo(String value) { channelRequestNo = value; }

    private Long amountMinor;
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private String channelTransactionNo;
    public String getChannelTransactionNo() { return channelTransactionNo; }
    public void setChannelTransactionNo(String value) { channelTransactionNo = value; }

    private LocalDateTime succeededAt;
    public LocalDateTime getSucceededAt() { return succeededAt; }
    public void setSucceededAt(LocalDateTime value) { succeededAt = value; }

    private String receiptId;
    public String getReceiptId() { return receiptId; }
    public void setReceiptId(String value) { receiptId = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
