/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.productcenter.client.dto;

import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.result.PageInfo;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import java.util.Collection;
import java.util.Objects;

/**
 * Decodes either a flat page or a failure envelope from the internal management protocol.
 */
public record ProductManagementPage<T>(Result result, Collection<T> data, PageInfo pageInfo) {
    public SuccessResponse<PageDTO<T>> asResponse() {
        return new SuccessResponse<>(
                result, data == null ? null : new PageDTO<>(data, Objects.requireNonNull(pageInfo)));
    }
}
