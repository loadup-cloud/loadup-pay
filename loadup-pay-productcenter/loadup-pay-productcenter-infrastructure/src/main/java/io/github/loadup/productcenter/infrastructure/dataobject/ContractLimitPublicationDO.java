/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.productcenter.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.components.database.dataobject.BaseDO;
import java.time.LocalDateTime;

@Table(value = "contract_limit_publication", mapperGenerateEnable = false)
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

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public Integer getContractRevision() {
        return contractRevision;
    }

    public void setContractRevision(Integer contractRevision) {
        this.contractRevision = contractRevision;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getPolicyHash() {
        return policyHash;
    }

    public void setPolicyHash(String policyHash) {
        this.policyHash = policyHash;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String payeeMerchantId) {
        this.payeeMerchantId = payeeMerchantId;
    }

    public String getProductScopeKey() {
        return productScopeKey;
    }

    public void setProductScopeKey(String productScopeKey) {
        this.productScopeKey = productScopeKey;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String snapshotHash) {
        this.snapshotHash = snapshotHash;
    }

    public String getActivationPayload() {
        return activationPayload;
    }

    public void setActivationPayload(String activationPayload) {
        this.activationPayload = activationPayload;
    }

    public String getOutboxEventId() {
        return outboxEventId;
    }

    public void setOutboxEventId(String outboxEventId) {
        this.outboxEventId = outboxEventId;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Integer getPublicationSchemaVersion() {
        return publicationSchemaVersion;
    }

    public void setPublicationSchemaVersion(Integer publicationSchemaVersion) {
        this.publicationSchemaVersion = publicationSchemaVersion;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
