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
import io.github.loadup.commons.json.ToStringAsJson;

@Table("order_refund_summary")
public class OrderRefundSummaryDO extends BaseDO {
    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String payeeMerchantId;
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }

    private Long orderAmountMinor;
    public Long getOrderAmountMinor() { return orderAmountMinor; }
    public void setOrderAmountMinor(Long value) { orderAmountMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private Long refundedMinor;
    public Long getRefundedMinor() { return refundedMinor; }
    public void setRefundedMinor(Long value) { refundedMinor = value; }

    private Long reservedMinor;
    public Long getReservedMinor() { return reservedMinor; }
    public void setReservedMinor(Long value) { reservedMinor = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
