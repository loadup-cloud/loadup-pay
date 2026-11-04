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
package io.github.loadup.merchantcenter.domain.model;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.merchantcenter.client.error.MerchantResultCodeEnum;
import java.time.LocalDateTime;
import java.util.Objects;

public record Merchant(
        String id,
        MerchantBasicInfo info,
        MerchantStatus status,
        long rowVersion,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public Merchant {
        Objects.requireNonNull(info);
        Objects.requireNonNull(status);
        if (rowVersion < 1) {
            throw new CommonException(MerchantResultCodeEnum.INVALID_MERCHANT_VERSION);
        }
    }

    public Merchant update(MerchantBasicInfo changes, String actor, LocalDateTime now) {
        if (!info.merchantCode().equals(changes.merchantCode())) {
            throw new CommonException(MerchantResultCodeEnum.MERCHANT_CODE_IS_IMMUTABLE);
        }
        return new Merchant(id, changes, status, Math.addExact(rowVersion, 1), createdBy, actor, createdAt, now);
    }

    public Merchant changeStatus(MerchantStatus target, String actor, LocalDateTime now) {
        return new Merchant(id, info, target, Math.addExact(rowVersion, 1), createdBy, actor, createdAt, now);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
