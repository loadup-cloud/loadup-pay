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
import io.github.loadup.commons.dataobject.BaseDO;
import java.time.LocalDateTime;

@Table("merchant_contract_revision")
public class ContractRevisionRow extends BaseDO {
    private String contractId;
    private int revision;
    private String snapshot;
    private String snapshotHash;

    public ContractRevisionRow() {}

    public ContractRevisionRow(
            String id,
            String contractId,
            int revision,
            String snapshot,
            String snapshotHash,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean deleted) {
        setId(id);
        this.contractId = contractId;
        this.revision = revision;
        this.snapshot = snapshot;
        this.snapshotHash = snapshotHash;
        setCreatedAt(createdAt);
        setUpdatedAt(updatedAt);
        setDeleted(deleted);
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String value) {
        this.contractId = value;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int value) {
        this.revision = value;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String value) {
        this.snapshot = value;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String value) {
        this.snapshotHash = value;
    }
}
