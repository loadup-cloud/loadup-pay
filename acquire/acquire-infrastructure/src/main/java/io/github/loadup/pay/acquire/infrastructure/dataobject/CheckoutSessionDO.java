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

@Table("checkout_session")
public class CheckoutSessionDO extends BaseDO {
    private String orderId;
    private String payeeMerchantId;
    private String requestKey;
    private String requestDigest;
    @DiagnosticHidden
    private String tokenHash;
    private String tokenNonce;
    private String tokenKeyRef;
    private String tokenKeyVersion;
    private String state;
    private LocalDateTime expiresAt;
    private String allowedOrigin;
    @DiagnosticHidden
    private String scopeSnapshot;
    private Long rowVersion;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String value) {
        requestKey = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String value) {
        tokenHash = value;
    }

    public String getTokenNonce() {
        return tokenNonce;
    }

    public void setTokenNonce(String value) {
        tokenNonce = value;
    }

    public String getTokenKeyRef() {
        return tokenKeyRef;
    }

    public void setTokenKeyRef(String value) {
        tokenKeyRef = value;
    }

    public String getTokenKeyVersion() {
        return tokenKeyVersion;
    }

    public void setTokenKeyVersion(String value) {
        tokenKeyVersion = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime value) {
        expiresAt = value;
    }

    public String getAllowedOrigin() {
        return allowedOrigin;
    }

    public void setAllowedOrigin(String value) {
        allowedOrigin = value;
    }

    public String getScopeSnapshot() {
        return scopeSnapshot;
    }

    public void setScopeSnapshot(String value) {
        scopeSnapshot = value;
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
