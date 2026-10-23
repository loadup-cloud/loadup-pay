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

@Table("refund_channel_receipt")
public class RefundChannelReceiptDO extends BaseDO {
    private String refundId;
    public String getRefundId() { return refundId; }
    public void setRefundId(String value) { refundId = value; }

    private String source;
    public String getSource() { return source; }
    public void setSource(String value) { source = value; }

    private String receiptKey;
    public String getReceiptKey() { return receiptKey; }
    public void setReceiptKey(String value) { receiptKey = value; }

    private String factsDigest;
    public String getFactsDigest() { return factsDigest; }
    public void setFactsDigest(String value) { factsDigest = value; }

    private String sourceDigest;
    public String getSourceDigest() { return sourceDigest; }
    public void setSourceDigest(String value) { sourceDigest = value; }

    @DiagnosticHidden private String observationPayload;
    public String getObservationPayload() { return observationPayload; }
    public void setObservationPayload(String value) { observationPayload = value; }

    private String disposition;
    public String getDisposition() { return disposition; }
    public void setDisposition(String value) { disposition = value; }

    private String reasonCode;
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }

    private LocalDateTime receivedAt;
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime value) { receivedAt = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
