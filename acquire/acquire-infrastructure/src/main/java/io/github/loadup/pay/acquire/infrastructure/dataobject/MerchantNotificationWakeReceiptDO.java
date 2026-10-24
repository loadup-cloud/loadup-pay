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

@Table("merchant_notification_wake_receipt")
public class MerchantNotificationWakeReceiptDO extends BaseDO {
    private String deliveryId;
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String value) { deliveryId = value; }

    private String eventId;
    public String getEventId() { return eventId; }
    public void setEventId(String value) { eventId = value; }

    private String requestKey;
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }

    private String callerScopeHash;
    public String getCallerScopeHash() { return callerScopeHash; }
    public void setCallerScopeHash(String value) { callerScopeHash = value; }

    private String principalType;
    public String getPrincipalType() { return principalType; }
    public void setPrincipalType(String value) { principalType = value; }

    private String principalId;
    public String getPrincipalId() { return principalId; }
    public void setPrincipalId(String value) { principalId = value; }

    @DiagnosticHidden
    private String reason;
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }

    private String requestDigest;
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }

    private String result;
    public String getResult() { return result; }
    public void setResult(String value) { result = value; }

    private Integer attemptCount;
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer value) { attemptCount = value; }

    private Long deliveryVersion;
    public Long getDeliveryVersion() { return deliveryVersion; }
    public void setDeliveryVersion(Long value) { deliveryVersion = value; }

    private String nativeTaskId;
    public String getNativeTaskId() { return nativeTaskId; }
    public void setNativeTaskId(String value) { nativeTaskId = value; }

    private java.time.LocalDateTime acceptedAt;
    public java.time.LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(java.time.LocalDateTime value) { acceptedAt = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
