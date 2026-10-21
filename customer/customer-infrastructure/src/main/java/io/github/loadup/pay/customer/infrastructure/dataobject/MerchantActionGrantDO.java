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
package io.github.loadup.pay.customer.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;
import java.time.LocalDateTime;

@Table("merchant_action_grant")
public class MerchantActionGrantDO extends BaseDO {
    private String principalType;

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String value) {
        principalType = value;
    }

    private String principalId;

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String value) {
        principalId = value;
    }

    private String payeeMerchantId;

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    private String relationshipId;

    public String getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(String value) {
        relationshipId = value;
    }

    private String action;

    public String getAction() {
        return action;
    }

    public void setAction(String value) {
        action = value;
    }

    private String state;

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    private LocalDateTime validFrom;

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime value) {
        validFrom = value;
    }

    private LocalDateTime validTo;

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime value) {
        validTo = value;
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
