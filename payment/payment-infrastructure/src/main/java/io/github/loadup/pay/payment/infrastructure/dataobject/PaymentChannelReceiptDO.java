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
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("payment_channel_receipt")
public class PaymentChannelReceiptDO extends BaseDO {
    private String paymentId;
    private String attemptId;
    private String channel;
    private String bindingRevisionId;
    private String accountScopeHash;
    private String channelRequestNo;
    private String source;
    private String receiptKey;
    private String factsDigest;
    private String sourceDigest;
    @DiagnosticHidden
    private String observationPayload;
    private String disposition;
    private String reasonCode;
    private LocalDateTime receivedAt;

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

    public String getSource() {
        return source;
    }

    public void setSource(String value) {
        source = value;
    }

    public String getReceiptKey() {
        return receiptKey;
    }

    public void setReceiptKey(String value) {
        receiptKey = value;
    }

    public String getFactsDigest() {
        return factsDigest;
    }

    public void setFactsDigest(String value) {
        factsDigest = value;
    }

    public String getSourceDigest() {
        return sourceDigest;
    }

    public void setSourceDigest(String value) {
        sourceDigest = value;
    }

    public String getObservationPayload() {
        return observationPayload;
    }

    public void setObservationPayload(String value) {
        observationPayload = value;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String value) {
        disposition = value;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String value) {
        reasonCode = value;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime value) {
        receivedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
