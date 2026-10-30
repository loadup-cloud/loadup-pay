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
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("payment_action")
public class PaymentActionDO extends BaseDO {
    private String paymentId;
    private String channel;
    private String bindingRevisionId;
    private String accountScopeHash;
    private String channelRequestNo;
    private Long amountMinor;
    private String currency;
    private Long executionEpoch;
    private String source;
    private String receiptId;
    private String sourceDigest;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String actionKind;
    @DiagnosticHidden
    private String actionPayload;
    private String payloadHash;

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
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

    public Long getExecutionEpoch() {
        return executionEpoch;
    }

    public void setExecutionEpoch(Long value) {
        executionEpoch = value;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String value) {
        source = value;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(String value) {
        receiptId = value;
    }

    public String getSourceDigest() {
        return sourceDigest;
    }

    public void setSourceDigest(String value) {
        sourceDigest = value;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime value) {
        issuedAt = value;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime value) {
        expiresAt = value;
    }

    public String getActionKind() {
        return actionKind;
    }

    public void setActionKind(String value) {
        actionKind = value;
    }

    public String getActionPayload() {
        return actionPayload;
    }

    public void setActionPayload(String value) {
        actionPayload = value;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String value) {
        payloadHash = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
