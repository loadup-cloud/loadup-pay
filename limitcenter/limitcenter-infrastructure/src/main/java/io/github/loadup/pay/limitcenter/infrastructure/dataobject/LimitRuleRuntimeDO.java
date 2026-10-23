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
    private Integer policyVersion;
    private String ruleScopeId;
    private String merchantId;
    private String metric;
    private String currency;
    private String windowType;
    private Integer weekStart;
    private String timezone;
    private Long currentLimit;
    private Boolean enforcementEnabled;
    private Boolean meteringEnabled;
    private Long rowVersion;

    public String getOwnerPolicyId() {
        return ownerPolicyId;
    }

    public void setOwnerPolicyId(String value) {
        ownerPolicyId = value;
    }

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer value) {
        policyVersion = value;
    }

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

    public Integer getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(Integer value) {
        weekStart = value;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String value) {
        timezone = value;
    }

    public Long getCurrentLimit() {
        return currentLimit;
    }

    public void setCurrentLimit(Long value) {
        currentLimit = value;
    }

    public Boolean getEnforcementEnabled() {
        return enforcementEnabled;
    }

    public void setEnforcementEnabled(Boolean value) {
        enforcementEnabled = value;
    }

    public Boolean getMeteringEnabled() {
        return meteringEnabled;
    }

    public void setMeteringEnabled(Boolean value) {
        meteringEnabled = value;
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
