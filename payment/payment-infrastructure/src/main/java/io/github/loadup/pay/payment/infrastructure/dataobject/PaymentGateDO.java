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

@Table("payment_gate")
public class PaymentGateDO extends BaseDO {
    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String paymentId;
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }

    private String payeeMerchantId;
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }

    private String callerServiceId;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private Long fenceEpoch;
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long value) { fenceEpoch = value; }

    private String reasonCode;
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
