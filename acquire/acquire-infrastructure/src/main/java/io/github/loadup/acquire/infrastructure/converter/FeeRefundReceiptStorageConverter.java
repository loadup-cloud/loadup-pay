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
package io.github.loadup.acquire.infrastructure.converter;

import io.github.loadup.acquire.domain.model.FeeRefundReceipt;
import io.github.loadup.acquire.infrastructure.dataobject.FeeRefundReceiptDO;
import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface FeeRefundReceiptStorageConverter {
    FeeRefundReceipt toDomain(FeeRefundReceiptDO row);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    FeeRefundReceiptDO toDO(FeeRefundReceipt value);
}
