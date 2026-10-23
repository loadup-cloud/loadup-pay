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
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("order_create_intent")
public class OrderCreateIntentDO extends BaseDO {
    private String payeeMerchantId;
    private String merchantOrderNo;
    private String orderId;
    private String requestDigest;
    private String callerScopeHash;
    private String entryMode;
    private String initialSessionId;
    @DiagnosticHidden
    private String intentPayload;
    private String state;
    private String admissionId;
    private LocalDateTime materializeBefore;
    private String resultCode;
    private Long rowVersion;

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String value) {
        merchantOrderNo = value;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
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

    public String getEntryMode() {
        return entryMode;
    }

    public void setEntryMode(String value) {
        entryMode = value;
    }

    public String getInitialSessionId() {
        return initialSessionId;
    }

    public void setInitialSessionId(String value) {
        initialSessionId = value;
    }

    public String getIntentPayload() {
        return intentPayload;
    }

    public void setIntentPayload(String value) {
        intentPayload = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public String getAdmissionId() {
        return admissionId;
    }

    public void setAdmissionId(String value) {
        admissionId = value;
    }

    public LocalDateTime getMaterializeBefore() {
        return materializeBefore;
    }

    public void setMaterializeBefore(LocalDateTime value) {
        materializeBefore = value;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String value) {
        resultCode = value;
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
