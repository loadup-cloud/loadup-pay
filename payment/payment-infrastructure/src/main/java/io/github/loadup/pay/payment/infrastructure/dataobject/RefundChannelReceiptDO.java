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

@Table("refund_channel_receipt")
public class RefundChannelReceiptDO extends BaseDO {
    private String refundId;
    private String source;
    private String receiptKey;
    private String factsDigest;
    private String sourceDigest;
    @DiagnosticHidden
    private String observationPayload;
    private String disposition;
    private String reasonCode;
    private LocalDateTime receivedAt;

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String value) {
        refundId = value;
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
