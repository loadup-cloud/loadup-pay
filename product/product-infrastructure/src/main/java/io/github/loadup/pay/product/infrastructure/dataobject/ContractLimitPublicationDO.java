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
package io.github.loadup.pay.product.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("contract_limit_publication")
public class ContractLimitPublicationDO extends BaseDO {
    private String contractId;
    private Integer contractRevision;
    private String policyId;
    private Integer policyVersion;
    private String policyHash;
    private String state;
    private LocalDateTime activatedAt;
    private Long rowVersion;
    private String payeeMerchantId;
    private String productScopeKey;
    private String snapshotHash;
    @DiagnosticHidden
    private String activationPayload;
    private String outboxEventId;
    private LocalDateTime requestedAt;
    private Integer publicationSchemaVersion;

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String value) {
        contractId = value;
    }

    public Integer getContractRevision() {
        return contractRevision;
    }

    public void setContractRevision(Integer value) {
        contractRevision = value;
    }

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

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime value) {
        activatedAt = value;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getProductScopeKey() {
        return productScopeKey;
    }

    public void setProductScopeKey(String value) {
        productScopeKey = value;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String value) {
        snapshotHash = value;
    }

    public String getActivationPayload() {
        return activationPayload;
    }

    public void setActivationPayload(String value) {
        activationPayload = value;
    }

    public String getOutboxEventId() {
        return outboxEventId;
    }

    public void setOutboxEventId(String value) {
        outboxEventId = value;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime value) {
        requestedAt = value;
    }

    public Integer getPublicationSchemaVersion() {
        return publicationSchemaVersion;
    }

    public void setPublicationSchemaVersion(Integer value) {
        publicationSchemaVersion = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
