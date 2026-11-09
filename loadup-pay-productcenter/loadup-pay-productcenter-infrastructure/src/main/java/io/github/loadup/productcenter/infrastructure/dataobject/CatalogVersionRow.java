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
import io.github.loadup.components.database.dataobject.BaseDO;
import java.time.LocalDateTime;

@Table("contract_catalog_version")
public class CatalogVersionRow extends BaseDO {
    private String kind;
    private String code;
    private int version;
    private String status;
    private long rowVersion;
    private String content;
    private String updatedBy;

    public CatalogVersionRow() {}

    public CatalogVersionRow(
            String id,
            String kind,
            String code,
            int version,
            String status,
            long rowVersion,
            String content,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean deleted) {
        setId(id);
        this.kind = kind;
        this.code = code;
        this.version = version;
        this.status = status;
        this.rowVersion = rowVersion;
        this.content = content;
        this.updatedBy = updatedBy;
        setCreatedAt(createdAt);
        setUpdatedAt(updatedAt);
        setDeleted(deleted);
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String value) {
        this.kind = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        this.code = value;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int value) {
        this.version = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        this.status = value;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(long value) {
        this.rowVersion = value;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String value) {
        this.content = value;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String value) {
        this.updatedBy = value;
    }
}
