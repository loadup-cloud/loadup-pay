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
package io.github.loadup.pay.limitcenter.infrastructure.dataobject;

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("limit_bucket")
public class LimitBucketDO extends BaseDO {
    private String ruleScopeId;
    private String merchantId;
    private String metric;
    private String currency;
    private String windowType;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Long usedValue;
    private Long heldValue;
    private Long rowVersion;

    public String getRuleScopeId() {
        return ruleScopeId;
    }

    public void setRuleScopeId(String value) {
        ruleScopeId = value;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String value) {
        merchantId = value;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String value) {
        metric = value;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    public String getWindowType() {
        return windowType;
    }

    public void setWindowType(String value) {
        windowType = value;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalDateTime value) {
        windowStart = value;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalDateTime value) {
        windowEnd = value;
    }

    public Long getUsedValue() {
        return usedValue;
    }

    public void setUsedValue(Long value) {
        usedValue = value;
    }

    public Long getHeldValue() {
        return heldValue;
    }

    public void setHeldValue(Long value) {
        heldValue = value;
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
