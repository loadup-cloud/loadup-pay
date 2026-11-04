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
package io.github.loadup.merchantcenter.app.service;

import io.github.loadup.merchantcenter.app.converter.MerchantConverter;
import io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO;
import io.github.loadup.merchantcenter.client.facade.MerchantQueryFacade;
import io.github.loadup.merchantcenter.domain.gateway.MerchantGateway;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MerchantQueryService implements MerchantQueryFacade {
    private final MerchantGateway gateway;
    private final MerchantConverter converter;

    public MerchantQueryService(MerchantGateway gateway, MerchantConverter converter) {
        this.gateway = gateway;
        this.converter = converter;
    }

    public Optional<MerchantProfileDTO> find(String id) {
        return gateway.find(id).map(converter::toProfile);
    }
}
