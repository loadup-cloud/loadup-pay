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
package io.github.loadup.pay.realtime.infrastructure.dataobject;

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

public class TradeRefundDO extends BaseDO {
    private String refundId;
    private String refundRequestId;
    private String paymentId;
    private String orderId;
    private String payeeMerchantId;
    private String state;
    private LocalDateTime businessCreatedAt;
    private Long sourceVersion;
    private String contentHash;
    @DiagnosticHidden
    private String payload;
    private LocalDateTime snapshotAt;

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String value) {
        refundId = value;
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

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public LocalDateTime getBusinessCreatedAt() {
        return businessCreatedAt;
    }

    public void setBusinessCreatedAt(LocalDateTime value) {
        businessCreatedAt = value;
    }

    public Long getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(Long value) {
        sourceVersion = value;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String value) {
        contentHash = value;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String value) {
        payload = value;
    }

    public LocalDateTime getSnapshotAt() {
        return snapshotAt;
    }

    public void setSnapshotAt(LocalDateTime value) {
        snapshotAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
