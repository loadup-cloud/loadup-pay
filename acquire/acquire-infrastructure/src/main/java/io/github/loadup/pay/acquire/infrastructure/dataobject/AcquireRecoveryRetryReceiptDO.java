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

public class AcquireRecoveryRetryReceiptDO extends BaseDO {
    private String requestKey;
    private String callerScopeHash;
    private String callerServiceId;
    private String requestDigest;
    private String kind;
    private String subjectId;
    private Long generation;
    private String orderId;
    private String nativeTaskId;
    private Integer expectedAttempts;
    private java.time.LocalDateTime expectedNextExecuteAt;
    @DiagnosticHidden
    private String reason;
    private String result;
    private java.time.LocalDateTime acceptedAt;

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String value) {
        requestKey = value;
    }

    public String getCallerScopeHash() {
        return callerScopeHash;
    }

    public void setCallerScopeHash(String value) {
        callerScopeHash = value;
    }

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String value) {
        kind = value;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String value) {
        subjectId = value;
    }

    public Long getGeneration() {
        return generation;
    }

    public void setGeneration(Long value) {
        generation = value;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getNativeTaskId() {
        return nativeTaskId;
    }

    public void setNativeTaskId(String value) {
        nativeTaskId = value;
    }

    public Integer getExpectedAttempts() {
        return expectedAttempts;
    }

    public void setExpectedAttempts(Integer value) {
        expectedAttempts = value;
    }

    public java.time.LocalDateTime getExpectedNextExecuteAt() {
        return expectedNextExecuteAt;
    }

    public void setExpectedNextExecuteAt(java.time.LocalDateTime value) {
        expectedNextExecuteAt = value;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String value) {
        reason = value;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String value) {
        result = value;
    }

    public java.time.LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(java.time.LocalDateTime value) {
        acceptedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
