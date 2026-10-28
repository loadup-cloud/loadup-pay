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

@Table("checkout_session_revocation_receipt")
public class CheckoutSessionRevocationReceiptDO extends BaseDO {
    private String sessionId;
    private String orderId;
    private String requestKey;
    private String callerScopeHash;
    private String requestDigest;
    @DiagnosticHidden
    private String reason;
    private Boolean newlyRevoked;
    private Long beforeVersion;
    private Long sessionVersion;
    private java.time.LocalDateTime revokedAt;
    private java.time.LocalDateTime acceptedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String value) {
        sessionId = value;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

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

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String value) {
        reason = value;
    }

    public Boolean getNewlyRevoked() {
        return newlyRevoked;
    }

    public void setNewlyRevoked(Boolean value) {
        newlyRevoked = value;
    }

    public Long getBeforeVersion() {
        return beforeVersion;
    }

    public void setBeforeVersion(Long value) {
        beforeVersion = value;
    }

    public Long getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Long value) {
        sessionVersion = value;
    }

    public java.time.LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(java.time.LocalDateTime value) {
        revokedAt = value;
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
