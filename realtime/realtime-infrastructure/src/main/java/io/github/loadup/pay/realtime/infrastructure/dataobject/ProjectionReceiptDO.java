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
package io.github.loadup.pay.realtime.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("trade_projection_receipt")
public class ProjectionReceiptDO extends BaseDO {
    private String deliveryId;
    private String businessEventId;
    private String producer;
    private String aggregateId;
    private Long sourceVersion;
    private Long projectedVersion;
    private String disposition;
    private String requestDigest;
    private String callerServiceId;
    private LocalDateTime acceptedAt;

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String value) {
        deliveryId = value;
    }

    public String getBusinessEventId() {
        return businessEventId;
    }

    public void setBusinessEventId(String value) {
        businessEventId = value;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String value) {
        producer = value;
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

    public Long getProjectedVersion() {
        return projectedVersion;
    }

    public void setProjectedVersion(Long value) {
        projectedVersion = value;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String value) {
        disposition = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime value) {
        acceptedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
