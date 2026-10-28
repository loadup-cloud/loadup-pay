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
package io.github.loadup.pay.acquire.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

@Table("fee_refund_receipt")
public class FeeRefundReceiptDO extends BaseDO {
    private String deliveryId;
    private String businessEventId;
    private String orderId;
    private Long sourceVersion;
    private Long appliedVersion;
    private String requestDigest;
    private String callerServiceId;
    private java.time.LocalDateTime acceptedAt;

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String value) {
        deliveryId = value;
    }

    public String getBusinessEventId() {
        return businessEventId;
    }

    public void setBusinessEventId(String value) {
        businessEventId = value;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public Long getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(Long value) {
        sourceVersion = value;
    }

    public Long getAppliedVersion() {
        return appliedVersion;
    }

    public void setAppliedVersion(Long value) {
        appliedVersion = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public java.time.LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(java.time.LocalDateTime value) {
        acceptedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
