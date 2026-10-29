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
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

public class PaymentRefundCapacityDO extends BaseDO {
    private String paymentId;
    private String orderId;
    private String payeeMerchantId;
    private LocalDateTime succeededAt;
    private Long succeededMinor;
    private String currency;
    private Long refundedMinor;
    private Long reservedMinor;
    private Long rowVersion;

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

    public LocalDateTime getSucceededAt() {
        return succeededAt;
    }

    public void setSucceededAt(LocalDateTime value) {
        succeededAt = value;
    }

    public Long getSucceededMinor() {
        return succeededMinor;
    }

    public void setSucceededMinor(Long value) {
        succeededMinor = value;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    public Long getRefundedMinor() {
        return refundedMinor;
    }

    public void setRefundedMinor(Long value) {
        refundedMinor = value;
    }

    public Long getReservedMinor() {
        return reservedMinor;
    }

    public void setReservedMinor(Long value) {
        reservedMinor = value;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
