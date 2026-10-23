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

@Table("merchant_relationship")
public class MerchantRelationshipDO extends BaseDO {
    private String parentMerchantId;
    private String childMerchantId;
    private String relationType;
    private String state;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Long rowVersion;

    public String getParentMerchantId() {
        return parentMerchantId;
    }

    public void setParentMerchantId(String value) {
        parentMerchantId = value;
    }

    public String getChildMerchantId() {
        return childMerchantId;
    }

    public void setChildMerchantId(String value) {
        childMerchantId = value;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String value) {
        relationType = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime value) {
        validFrom = value;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime value) {
        validTo = value;
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
