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
import io.github.loadup.commons.json.*;
import java.time.LocalDateTime;

@Table("order_fee")
public class OrderFeeDO extends BaseDO {
    private String orderId;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    private String feeType;

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String value) {
        feeType = value;
    }

    private String currency;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    private Long baseAmountMinor;

    public Long getBaseAmountMinor() {
        return baseAmountMinor;
    }

    public void setBaseAmountMinor(Long value) {
        baseAmountMinor = value;
    }

    private Long feeAmountMinor;

    public Long getFeeAmountMinor() {
        return feeAmountMinor;
    }

    public void setFeeAmountMinor(Long value) {
        feeAmountMinor = value;
    }

    @DiagnosticHidden
    private String feeRuleSnapshot;

    public String getFeeRuleSnapshot() {
        return feeRuleSnapshot;
    }

    public void setFeeRuleSnapshot(String value) {
        feeRuleSnapshot = value;
    }

    private Long refundBasisVersion;

    public Long getRefundBasisVersion() {
        return refundBasisVersion;
    }

    public void setRefundBasisVersion(Long value) {
        refundBasisVersion = value;
    }

    private Long refundBasisMinor;

    public Long getRefundBasisMinor() {
        return refundBasisMinor;
    }

    public void setRefundBasisMinor(Long value) {
        refundBasisMinor = value;
    }

    private Long adjustedAmountMinor;

    public Long getAdjustedAmountMinor() {
        return adjustedAmountMinor;
    }

    public void setAdjustedAmountMinor(Long value) {
        adjustedAmountMinor = value;
    }

    private String state;

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    private LocalDateTime confirmedAt;

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime value) {
        confirmedAt = value;
    }

    private Long rowVersion;

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
