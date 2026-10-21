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
package io.github.loadup.pay.acquire.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.*;
import java.time.LocalDateTime;

@Table("order_contract_snapshot")
public class OrderContractSnapshotDO extends BaseDO {
    private String orderId;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

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

    private Long contractGeneration;

    public Long getContractGeneration() {
        return contractGeneration;
    }

    public void setContractGeneration(Long value) {
        contractGeneration = value;
    }

    private String snapshotHash;

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String value) {
        snapshotHash = value;
    }

    private String compiledTermsHash;

    public String getCompiledTermsHash() {
        return compiledTermsHash;
    }

    public void setCompiledTermsHash(String value) {
        compiledTermsHash = value;
    }

    private Integer schemaVersion;

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer value) {
        schemaVersion = value;
    }

    @DiagnosticHidden
    private String configuration;

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String value) {
        configuration = value;
    }

    private String payloadHash;

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String value) {
        payloadHash = value;
    }

    private String admissionDigest;

    public String getAdmissionDigest() {
        return admissionDigest;
    }

    public void setAdmissionDigest(String value) {
        admissionDigest = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
