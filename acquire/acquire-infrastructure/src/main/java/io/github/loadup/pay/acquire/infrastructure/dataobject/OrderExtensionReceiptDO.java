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
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

public class OrderExtensionReceiptDO extends BaseDO {
    private String orderId;
    private String callerScopeHash;
    private String requestKey;
    private String requestDigest;
    private LocalDateTime originalDueAt;
    private LocalDateTime previousDueAt;
    private LocalDateTime requestedDueAt;
    private Long expectedOrderVersion;
    private Long deadlineVersion;
    private LocalDateTime appliedAt;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getCallerScopeHash() {
        return callerScopeHash;
    }

    public void setCallerScopeHash(String value) {
        callerScopeHash = value;
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

    public LocalDateTime getOriginalDueAt() {
        return originalDueAt;
    }

    public void setOriginalDueAt(LocalDateTime value) {
        originalDueAt = value;
    }

    public LocalDateTime getPreviousDueAt() {
        return previousDueAt;
    }

    public void setPreviousDueAt(LocalDateTime value) {
        previousDueAt = value;
    }

    public LocalDateTime getRequestedDueAt() {
        return requestedDueAt;
    }

    public void setRequestedDueAt(LocalDateTime value) {
        requestedDueAt = value;
    }

    public Long getExpectedOrderVersion() {
        return expectedOrderVersion;
    }

    public void setExpectedOrderVersion(Long value) {
        expectedOrderVersion = value;
    }

    public Long getDeadlineVersion() {
        return deadlineVersion;
    }

    public void setDeadlineVersion(Long value) {
        deadlineVersion = value;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime value) {
        appliedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
