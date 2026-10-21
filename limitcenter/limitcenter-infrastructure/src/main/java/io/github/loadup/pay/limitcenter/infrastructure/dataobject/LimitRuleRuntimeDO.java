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

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

@Table("limit_rule_runtime")
public class LimitRuleRuntimeDO extends BaseDO {
    private String ownerPolicyId;

    public String getOwnerPolicyId() {
        return ownerPolicyId;
    }

    public void setOwnerPolicyId(String value) {
        ownerPolicyId = value;
    }

    private Integer policyVersion;

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer value) {
        policyVersion = value;
    }

    private String ruleScopeId;

    public String getRuleScopeId() {
        return ruleScopeId;
    }

    public void setRuleScopeId(String value) {
        ruleScopeId = value;
    }

    private String merchantId;

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String value) {
        merchantId = value;
    }

    private String metric;

    public String getMetric() {
        return metric;
    }

    public void setMetric(String value) {
        metric = value;
    }

    private String currency;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    private String windowType;

    public String getWindowType() {
        return windowType;
    }

    public void setWindowType(String value) {
        windowType = value;
    }

    private Integer weekStart;

    public Integer getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(Integer value) {
        weekStart = value;
    }

    private String timezone;

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String value) {
        timezone = value;
    }

    private Long currentLimit;

    public Long getCurrentLimit() {
        return currentLimit;
    }

    public void setCurrentLimit(Long value) {
        currentLimit = value;
    }

    private Boolean enforcementEnabled;

    public Boolean getEnforcementEnabled() {
        return enforcementEnabled;
    }

    public void setEnforcementEnabled(Boolean value) {
        enforcementEnabled = value;
    }

    private Boolean meteringEnabled;

    public Boolean getMeteringEnabled() {
        return meteringEnabled;
    }

    public void setMeteringEnabled(Boolean value) {
        meteringEnabled = value;
    }

    private Long rowVersion;

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
