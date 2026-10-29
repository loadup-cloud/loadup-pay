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

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

public class PaymentEventReceiptDO extends BaseDO {
    private String deliveryId;
    private String businessEventId;
    private String paymentId;
    private String orderId;
    private String allocationId;
    private Long paymentVersion;
    private String evidenceId;
    private String requestDigest;
    private String callerServiceId;
    private Long orderVersion;
    private LocalDateTime acceptedAt;

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

    public String getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(String value) {
        allocationId = value;
    }

    public Long getPaymentVersion() {
        return paymentVersion;
    }

    public void setPaymentVersion(Long value) {
        paymentVersion = value;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String value) {
        evidenceId = value;
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

    public Long getOrderVersion() {
        return orderVersion;
    }

    public void setOrderVersion(Long value) {
        orderVersion = value;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime value) {
        acceptedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
