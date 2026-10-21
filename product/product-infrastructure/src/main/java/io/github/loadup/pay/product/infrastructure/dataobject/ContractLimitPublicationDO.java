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

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String value) {
        contractId = value;
    }

    private Integer contractRevision;

    public Integer getContractRevision() {
        return contractRevision;
    }

    public void setContractRevision(Integer value) {
        contractRevision = value;
    }

    private String policyId;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String value) {
        policyId = value;
    }

    private Integer policyVersion;

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Integer value) {
        policyVersion = value;
    }

    private String policyHash;

    public String getPolicyHash() {
        return policyHash;
    }

    public void setPolicyHash(String value) {
        policyHash = value;
    }

    private String state;

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    private LocalDateTime activatedAt;

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime value) {
        activatedAt = value;
    }

    private Long rowVersion;

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    private String payeeMerchantId;

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    private String productScopeKey;

    public String getProductScopeKey() {
        return productScopeKey;
    }

    public void setProductScopeKey(String value) {
        productScopeKey = value;
    }

    private String snapshotHash;

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String value) {
        snapshotHash = value;
    }

    @DiagnosticHidden
    private String activationPayload;

    public String getActivationPayload() {
        return activationPayload;
    }

    public void setActivationPayload(String value) {
        activationPayload = value;
    }

    private String outboxEventId;

    public String getOutboxEventId() {
        return outboxEventId;
    }

    public void setOutboxEventId(String value) {
        outboxEventId = value;
    }

    private LocalDateTime requestedAt;

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime value) {
        requestedAt = value;
    }

    private Integer publicationSchemaVersion;

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
