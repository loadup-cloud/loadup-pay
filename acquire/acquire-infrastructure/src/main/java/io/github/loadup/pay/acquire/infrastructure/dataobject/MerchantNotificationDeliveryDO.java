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
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("merchant_notification_delivery")
public class MerchantNotificationDeliveryDO extends BaseDO {
    private String eventId;
    public String getEventId() { return eventId; }
    public void setEventId(String value) { eventId = value; }

    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String payeeMerchantId;
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }

    private String eventType;
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }

    private String aggregateId;
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String value) { aggregateId = value; }

    private Long sourceVersion;
    public Long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Long value) { sourceVersion = value; }

    @DiagnosticHidden
    private String body;
    public String getBody() { return body; }
    public void setBody(String value) { body = value; }

    private String bodyDigest;
    public String getBodyDigest() { return bodyDigest; }
    public void setBodyDigest(String value) { bodyDigest = value; }

    private String targetRef;
    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String value) { targetRef = value; }

    private String signingIdentityRef;
    public String getSigningIdentityRef() { return signingIdentityRef; }
    public void setSigningIdentityRef(String value) { signingIdentityRef = value; }

    private Integer timeoutMillis;
    public Integer getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(Integer value) { timeoutMillis = value; }

    private Long retryInitialSeconds;
    public Long getRetryInitialSeconds() { return retryInitialSeconds; }
    public void setRetryInitialSeconds(Long value) { retryInitialSeconds = value; }

    private BigDecimal retryMultiplier;
    public BigDecimal getRetryMultiplier() { return retryMultiplier; }
    public void setRetryMultiplier(BigDecimal value) { retryMultiplier = value; }

    private Long retryMaximumSeconds;
    public Long getRetryMaximumSeconds() { return retryMaximumSeconds; }
    public void setRetryMaximumSeconds(Long value) { retryMaximumSeconds = value; }

    private Integer retryMaxAttempts;
    public Integer getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(Integer value) { retryMaxAttempts = value; }

    private Long retryWindowSeconds;
    public Long getRetryWindowSeconds() { return retryWindowSeconds; }
    public void setRetryWindowSeconds(Long value) { retryWindowSeconds = value; }

    private LocalDateTime occurredAt;
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime value) { occurredAt = value; }

    private LocalDateTime expiresAt;
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { expiresAt = value; }

    private LocalDateTime nextAttemptAt;
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime value) { nextAttemptAt = value; }

    private Integer attemptCount;
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer value) { attemptCount = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private String routeFingerprint;
    public String getRouteFingerprint() { return routeFingerprint; }
    public void setRouteFingerprint(String value) { routeFingerprint = value; }

    private LocalDateTime acknowledgedAt;
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime value) { acknowledgedAt = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
