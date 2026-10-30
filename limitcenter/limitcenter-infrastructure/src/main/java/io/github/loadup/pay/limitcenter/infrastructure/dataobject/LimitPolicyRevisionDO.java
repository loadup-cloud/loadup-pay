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
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("limit_policy_revision")
public class LimitPolicyRevisionDO extends BaseDO {
    private String policyId;
    private Integer policyVersion;
    private String policyHash;
    private String payeeMerchantId;
    @DiagnosticHidden
    private String compiledRules;
    private LocalDateTime activatedAt;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String value) {
        policyId = value;
    }

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer value) {
        policyVersion = value;
    }

    public String getPolicyHash() {
        return policyHash;
    }

    public void setPolicyHash(String value) {
        policyHash = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getCompiledRules() {
        return compiledRules;
    }

    public void setCompiledRules(String value) {
        compiledRules = value;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime value) {
        activatedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
