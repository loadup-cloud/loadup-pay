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

import io.github.loadup.merchantcenter.client.dto.MerchantPersistenceFacts;
import io.github.loadup.merchantcenter.client.facade.MerchantPersistenceFacade;
import io.github.loadup.merchantcenter.domain.gateway.MerchantGateway;
import io.github.loadup.merchantcenter.domain.model.Merchant;
import io.github.loadup.merchantcenter.domain.model.MerchantStatus;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class MerchantPersistenceService implements MerchantPersistenceFacade {
    private final MerchantGateway gateway;

    public MerchantPersistenceService(MerchantGateway gateway) {
        this.gateway = gateway;
    }

    private static MerchantPersistenceFacts facts(Merchant merchant) {
        return new MerchantPersistenceFacts(
                merchant.id(), merchant.status() == MerchantStatus.ACTIVE, merchant.rowVersion());
    }

    @Override
    public Optional<MerchantPersistenceFacts> find(String id) {
        return gateway.find(id).map(MerchantPersistenceService::facts);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MerchantPersistenceFacts> lock(String id) {
        return gateway.lock(id).map(MerchantPersistenceService::facts);
    }
}
