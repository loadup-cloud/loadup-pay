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

public class OrderPaymentAllocationDO extends BaseDO {
    private String orderId;
    private String paymentId;
    private String actorScope;
    private String actionKey;
    private String requestDigest;
    private String callerScopeHash;
    private Long amountMinor;
    private String currency;
    private String state;
    private Long paymentSourceVersion;
    @DiagnosticHidden
    private String intentPayload;
    private String payloadHash;
    private Long rowVersion;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    public String getActorScope() {
        return actorScope;
    }

    public void setActorScope(String value) {
        actorScope = value;
    }

    public String getActionKey() {
        return actionKey;
    }

    public void setActionKey(String value) {
        actionKey = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getCallerScopeHash() {
        return callerScopeHash;
    }

    public void setCallerScopeHash(String value) {
        callerScopeHash = value;
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

    public Long getPaymentSourceVersion() {
        return paymentSourceVersion;
    }

    public void setPaymentSourceVersion(Long value) {
        paymentSourceVersion = value;
    }

    public String getIntentPayload() {
        return intentPayload;
    }

    public void setIntentPayload(String value) {
        intentPayload = value;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String value) {
        payloadHash = value;
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
