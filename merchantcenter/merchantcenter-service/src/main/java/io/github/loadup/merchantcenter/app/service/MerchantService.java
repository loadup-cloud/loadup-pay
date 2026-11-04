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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.merchantcenter.app.converter.MerchantConverter;
import io.github.loadup.merchantcenter.client.command.MerchantCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantStatusCommand;
import io.github.loadup.merchantcenter.client.command.MerchantUpdateCommand;
import io.github.loadup.merchantcenter.client.dto.MerchantDTO;
import io.github.loadup.merchantcenter.client.error.MerchantResultCodeEnum;
import io.github.loadup.merchantcenter.client.facade.MerchantFacade;
import io.github.loadup.merchantcenter.client.query.MerchantPageQuery;
import io.github.loadup.merchantcenter.domain.gateway.MerchantGateway;
import io.github.loadup.merchantcenter.domain.model.Merchant;
import io.github.loadup.merchantcenter.domain.model.MerchantStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService implements MerchantFacade {
    private final MerchantGateway gateway;
    private final MerchantConverter converter;
    private final Clock clock;

    public MerchantService(MerchantGateway gateway, MerchantConverter converter, Clock clock) {
        this.gateway = gateway;
        this.converter = converter;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    @Transactional
    public MerchantDTO create(MerchantCreateCommand command, String actor) {
        MerchantIdentity.actor(actor);
        var at = now();
        var merchant = new Merchant(
                UUID.randomUUID().toString(),
                converter.toInfo(command),
                MerchantStatus.ACTIVE,
                1,
                actor,
                actor,
                at,
                at);
        gateway.insert(merchant);
        return converter.toDTO(merchant);
    }

    @Transactional
    public MerchantDTO update(MerchantUpdateCommand command, String actor) {
        MerchantIdentity.actor(actor);
        var current = require(command.id());
        expected(current, command.expectedRowVersion());
        var updated = current.update(converter.merge(converter.toInfo(command), current.info()), actor, now());
        gateway.update(updated, current.rowVersion());
        return converter.toDTO(updated);
    }

    @Transactional
    public MerchantDTO changeStatus(MerchantStatusCommand command, String actor) {
        MerchantIdentity.actor(actor);
        var current = require(command.id());
        expected(current, command.expectedRowVersion());
        var updated = current.changeStatus(MerchantStatus.valueOf(command.status()), actor, now());
        gateway.update(updated, current.rowVersion());
        return converter.toDTO(updated);
    }

    private void expected(Merchant current, long expected) {
        if (current.rowVersion() != expected) {
            throw new CommonException(MerchantResultCodeEnum.CONFLICT);
        }
    }

    private Merchant require(String id) {
        return gateway.find(id).orElseThrow(() -> new CommonException(MerchantResultCodeEnum.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MerchantDTO detail(String id) {
        return converter.toDTO(require(id));
    }

    @Transactional(readOnly = true)
    public PageDTO<MerchantDTO> page(MerchantPageQuery query) {
        if (query.page() < 1 || query.size() < 1 || query.size() > 100) {
            throw new CommonException(MerchantResultCodeEnum.INVALID_PAGE);
        }
        var result = gateway.page(
                query.merchantCode(),
                query.name(),
                query.status() == null || query.status().isBlank() ? null : MerchantStatus.valueOf(query.status()),
                query.page(),
                query.size());
        return PageDTO.of(
                result.records().stream().map(converter::toDTO).toList(), result.total(), query.page(), query.size());
    }
}
