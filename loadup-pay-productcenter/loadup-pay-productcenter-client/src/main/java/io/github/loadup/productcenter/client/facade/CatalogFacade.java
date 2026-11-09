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
package io.github.loadup.productcenter.client.facade;

import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.productcenter.client.command.CatalogActionCommand;
import io.github.loadup.productcenter.client.command.CatalogSaveCommand;
import io.github.loadup.productcenter.client.dto.CatalogVersionDTO;
import io.github.loadup.productcenter.client.query.CatalogPageQuery;

public interface CatalogFacade {
    CatalogVersionDTO save(CatalogSaveCommand command, String actor);

    CatalogVersionDTO publish(CatalogActionCommand command, String actor);

    CatalogVersionDTO retire(CatalogActionCommand command, String actor);

    CatalogVersionDTO detail(String id);

    PageDTO<CatalogVersionDTO> page(CatalogPageQuery query);
}
