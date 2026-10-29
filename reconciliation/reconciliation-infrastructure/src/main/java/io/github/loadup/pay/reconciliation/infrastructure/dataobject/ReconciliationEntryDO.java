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
package io.github.loadup.pay.reconciliation.infrastructure.dataobject;

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

public class ReconciliationEntryDO extends BaseDO {
    private String inputId;
    private Long recordNumber;
    private String matchKeyHash;
    private String kind;
    @io.github.loadup.commons.json.DiagnosticHidden
    private String channelTransactionNo;
    @io.github.loadup.commons.json.DiagnosticHidden
    private String channelRefundNo;
    @io.github.loadup.commons.json.DiagnosticHidden
    private String merchantPaymentNo;
    private Long amountMinor;
    private String currency;
    private String state;

    public String getInputId() {
        return inputId;
    }

    public void setInputId(String value) {
        inputId = value;
    }

    public Long getRecordNumber() {
        return recordNumber;
    }

    public void setRecordNumber(Long value) {
        recordNumber = value;
    }

    public String getMatchKeyHash() {
        return matchKeyHash;
    }

    public void setMatchKeyHash(String value) {
        matchKeyHash = value;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String value) {
        kind = value;
    }

    public String getChannelTransactionNo() {
        return channelTransactionNo;
    }

    public void setChannelTransactionNo(String value) {
        channelTransactionNo = value;
    }

    public String getChannelRefundNo() {
        return channelRefundNo;
    }

    public void setChannelRefundNo(String value) {
        channelRefundNo = value;
    }

    public String getMerchantPaymentNo() {
        return merchantPaymentNo;
    }

    public void setMerchantPaymentNo(String value) {
        merchantPaymentNo = value;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long value) {
        amountMinor = value;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
