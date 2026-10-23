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
import java.time.LocalDateTime;

@Table("acquire_payment_event_receipt")
public class PaymentEventReceiptDO extends BaseDO {
    private String deliveryId;
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String value) { deliveryId = value; }

    private String businessEventId;
    public String getBusinessEventId() { return businessEventId; }
    public void setBusinessEventId(String value) { businessEventId = value; }

    private String paymentId;
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }

    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String allocationId;
    public String getAllocationId() { return allocationId; }
    public void setAllocationId(String value) { allocationId = value; }

    private Long paymentVersion;
    public Long getPaymentVersion() { return paymentVersion; }
    public void setPaymentVersion(Long value) { paymentVersion = value; }

    private String evidenceId;
    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String value) { evidenceId = value; }

    private String requestDigest;
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }

    private String callerServiceId;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }

    private Long orderVersion;
    public Long getOrderVersion() { return orderVersion; }
    public void setOrderVersion(Long value) { orderVersion = value; }

    private LocalDateTime acceptedAt;
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime value) { acceptedAt = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
