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
package io.github.loadup.merchantcenter.client.facade;

import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.merchantcenter.client.command.MerchantCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantStatusCommand;
import io.github.loadup.merchantcenter.client.command.MerchantUpdateCommand;
import io.github.loadup.merchantcenter.client.dto.MerchantDTO;
import io.github.loadup.merchantcenter.client.query.MerchantPageQuery;

public interface MerchantFacade {
    MerchantDTO create(MerchantCreateCommand command, String actor);

    MerchantDTO update(MerchantUpdateCommand command, String actor);

    MerchantDTO changeStatus(MerchantStatusCommand command, String actor);

    MerchantDTO detail(String id);

    PageDTO<MerchantDTO> page(MerchantPageQuery query);
}
