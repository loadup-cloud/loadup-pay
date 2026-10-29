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

import java.time.LocalDateTime;

public class PaymentResultAnomalyDO extends BaseDO {
    private String paymentId;
    private String attemptId;
    private String receiptId;
    private String reasonCode;
    private String factsDigest;
    @DiagnosticHidden
    private String observationPayload;
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

    public String getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(String value) {
        receiptId = value;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String value) {
        reasonCode = value;
    }

    public String getFactsDigest() {
        return factsDigest;
    }

    public void setFactsDigest(String value) {
        factsDigest = value;
    }

    public String getObservationPayload() {
        return observationPayload;
    }

    public void setObservationPayload(String value) {
        observationPayload = value;
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
