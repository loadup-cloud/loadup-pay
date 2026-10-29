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
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

public class OrderFeeDO extends BaseDO {
    private String orderId;
    private String feeType;
    private String currency;
    private Long baseAmountMinor;
    private Long feeAmountMinor;
    @DiagnosticHidden
    private String feeRuleSnapshot;
    private Long refundBasisVersion;
    private Long refundBasisMinor;
    private Long adjustedAmountMinor;
    private String state;
    private LocalDateTime confirmedAt;
    private Long rowVersion;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String value) {
        feeType = value;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    public Long getBaseAmountMinor() {
        return baseAmountMinor;
    }

    public void setBaseAmountMinor(Long value) {
        baseAmountMinor = value;
    }

    public Long getFeeAmountMinor() {
        return feeAmountMinor;
    }

    public void setFeeAmountMinor(Long value) {
        feeAmountMinor = value;
    }

    public String getFeeRuleSnapshot() {
        return feeRuleSnapshot;
    }

    public void setFeeRuleSnapshot(String value) {
        feeRuleSnapshot = value;
    }

    public Long getRefundBasisVersion() {
        return refundBasisVersion;
    }

    public void setRefundBasisVersion(Long value) {
        refundBasisVersion = value;
    }

    public Long getRefundBasisMinor() {
        return refundBasisMinor;
    }

    public void setRefundBasisMinor(Long value) {
        refundBasisMinor = value;
    }

    public Long getAdjustedAmountMinor() {
        return adjustedAmountMinor;
    }

    public void setAdjustedAmountMinor(Long value) {
        adjustedAmountMinor = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime value) {
        confirmedAt = value;
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
