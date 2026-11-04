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
package io.github.loadup.productcenter.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.productcenter.app.converter.ContractConverter;
import io.github.loadup.productcenter.app.support.CatalogAssembler;
import io.github.loadup.productcenter.app.support.ContractCodec;
import io.github.loadup.productcenter.app.support.ContractIdentity;
import io.github.loadup.productcenter.client.command.CatalogActionCommand;
import io.github.loadup.productcenter.client.command.CatalogSaveCommand;
import io.github.loadup.productcenter.client.dto.CatalogVersionDTO;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.client.facade.CatalogFacade;
import io.github.loadup.productcenter.client.query.CatalogPageQuery;
import io.github.loadup.productcenter.domain.gateway.CatalogGateway;
import io.github.loadup.productcenter.domain.model.CatalogEntry;
import io.github.loadup.productcenter.domain.model.CatalogKind;
import io.github.loadup.productcenter.domain.model.CatalogStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService implements CatalogFacade {
    private final CatalogGateway gateway;
    private final ContractCodec codec;
    private final CatalogAssembler assembler;
    private final ContractConverter converter;
    private final Clock clock;

    public CatalogService(
            CatalogGateway gateway,
            ContractCodec codec,
            CatalogAssembler assembler,
            ContractConverter converter,
            Clock clock) {
        this.gateway = gateway;
        this.codec = codec;
        this.assembler = assembler;
        this.converter = converter;
        this.clock = clock;
    }

    @Transactional
    public CatalogVersionDTO save(CatalogSaveCommand command, String actor) {
        ContractIdentity.actor(actor);
        String code = ContractIdentity.code(command.code(), 128);
        CatalogKind kind = CatalogKind.valueOf(command.kind());
        if (command.version() < 1 || command.definition() == null) {
            throw new CommonException(ContractResultCodeEnum.INVALID_CATALOG_DEFINITION);
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        CatalogEntry previous = command.id() == null
                ? null
                : gateway.lock(command.id()).orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND));
        if (previous != null
                && (previous.status() != CatalogStatus.DRAFT
                        || command.expectedRowVersion() == null
                        || previous.rowVersion() != command.expectedRowVersion()
                        || previous.kind() != kind
                        || !previous.code().equals(code)
                        || previous.version() != command.version())) {
            throw new CommonException(ContractResultCodeEnum.DRAFT_NOT_EDITABLE);
        }
        CatalogEntry entry = new CatalogEntry(
                previous == null ? UUID.randomUUID().toString() : previous.id(),
                kind,
                code,
                command.version(),
                CatalogStatus.DRAFT,
                previous == null ? 1 : Math.addExact(previous.rowVersion(), 1),
                codec.write(command.definition()),
                actor,
                previous == null ? now : previous.createdAt(),
                now);
        assembler.validate(entry, false);
        if (previous == null) {
            gateway.insert(entry);
        } else {
            gateway.update(entry, previous.rowVersion());
        }
        return converter.toCatalog(entry);
    }

    @Transactional
    public CatalogVersionDTO publish(CatalogActionCommand command, String actor) {
        return transition(command, actor, CatalogStatus.DRAFT, CatalogStatus.PUBLISHED);
    }

    @Transactional
    public CatalogVersionDTO retire(CatalogActionCommand command, String actor) {
        return transition(command, actor, CatalogStatus.PUBLISHED, CatalogStatus.RETIRED);
    }

    private CatalogVersionDTO transition(
            CatalogActionCommand command, String actor, CatalogStatus from, CatalogStatus to) {
        ContractIdentity.actor(actor);
        CatalogEntry entry =
                gateway.lock(command.id()).orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND));
        if (entry.rowVersion() != command.expectedRowVersion()) {
            throw new CommonException(ContractResultCodeEnum.CONFLICT);
        }
        if (entry.status() != from) {
            throw new CommonException(ContractResultCodeEnum.INVALID_STATE);
        }
        if (to == CatalogStatus.PUBLISHED) {
            assembler.validate(entry, true);
        }
        var updated = new CatalogEntry(
                entry.id(),
                entry.kind(),
                entry.code(),
                entry.version(),
                to,
                Math.addExact(entry.rowVersion(), 1),
                entry.content(),
                actor,
                entry.createdAt(),
                LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
        gateway.update(updated, entry.rowVersion());
        return converter.toCatalog(updated);
    }

    @Transactional(readOnly = true)
    public CatalogVersionDTO detail(String id) {
        return converter.toCatalog(
                gateway.find(id).orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public PageDTO<CatalogVersionDTO> page(CatalogPageQuery query) {
        ContractIdentity.page(query.page(), query.size());
        var page = gateway.page(
                CatalogKind.valueOf(query.kind()),
                query.status() == null ? null : CatalogStatus.valueOf(query.status()),
                query.code(),
                query.page(),
                query.size());
        return PageDTO.of(
                page.records().stream().map(converter::toCatalog).toList(), page.total(), query.page(), query.size());
    }
}
