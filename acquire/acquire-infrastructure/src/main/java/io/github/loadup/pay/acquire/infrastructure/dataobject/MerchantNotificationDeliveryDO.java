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
    private String orderId;
    private String payeeMerchantId;
    private String eventType;
    private String aggregateId;
    private Long sourceVersion;
    @DiagnosticHidden
    private String body;
    private String bodyDigest;
    private String targetRef;
    private String signingIdentityRef;
    private Integer timeoutMillis;
    private Long retryInitialSeconds;
    private BigDecimal retryMultiplier;
    private Long retryMaximumSeconds;
    private Integer retryMaxAttempts;
    private Long retryWindowSeconds;
    private LocalDateTime occurredAt;
    private LocalDateTime expiresAt;
    private LocalDateTime nextAttemptAt;
    private Integer attemptCount;
    private String state;
    private String routeFingerprint;
    private LocalDateTime acknowledgedAt;
    private Long rowVersion;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String value) {
        eventId = value;
    }

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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String value) {
        eventType = value;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String value) {
        aggregateId = value;
    }

    public Long getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(Long value) {
        sourceVersion = value;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String value) {
        body = value;
    }

    public String getBodyDigest() {
        return bodyDigest;
    }

    public void setBodyDigest(String value) {
        bodyDigest = value;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public void setTargetRef(String value) {
        targetRef = value;
    }

    public String getSigningIdentityRef() {
        return signingIdentityRef;
    }

    public void setSigningIdentityRef(String value) {
        signingIdentityRef = value;
    }

    public Integer getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(Integer value) {
        timeoutMillis = value;
    }

    public Long getRetryInitialSeconds() {
        return retryInitialSeconds;
    }

    public void setRetryInitialSeconds(Long value) {
        retryInitialSeconds = value;
    }

    public BigDecimal getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(BigDecimal value) {
        retryMultiplier = value;
    }

    public Long getRetryMaximumSeconds() {
        return retryMaximumSeconds;
    }

    public void setRetryMaximumSeconds(Long value) {
        retryMaximumSeconds = value;
    }

    public Integer getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(Integer value) {
        retryMaxAttempts = value;
    }

    public Long getRetryWindowSeconds() {
        return retryWindowSeconds;
    }

    public void setRetryWindowSeconds(Long value) {
        retryWindowSeconds = value;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime value) {
        occurredAt = value;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime value) {
        expiresAt = value;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime value) {
        nextAttemptAt = value;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer value) {
        attemptCount = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public String getRouteFingerprint() {
        return routeFingerprint;
    }

    public void setRouteFingerprint(String value) {
        routeFingerprint = value;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime value) {
        acknowledgedAt = value;
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
