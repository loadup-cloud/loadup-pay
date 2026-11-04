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

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.productcenter.app.converter.ContractConverter;
import io.github.loadup.productcenter.app.support.CatalogAssembler;
import io.github.loadup.productcenter.app.support.ContractCodec;
import io.github.loadup.productcenter.app.support.ContractIdentity;
import io.github.loadup.productcenter.app.support.MerchantFacts;
import io.github.loadup.productcenter.client.command.ContractSignCommand;
import io.github.loadup.productcenter.client.command.ContractStatusCommand;
import io.github.loadup.productcenter.client.dto.MerchantContractDTO;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.client.facade.MerchantContractFacade;
import io.github.loadup.productcenter.client.query.MerchantContractPageQuery;
import io.github.loadup.productcenter.domain.gateway.CatalogGateway;
import io.github.loadup.productcenter.domain.gateway.MerchantContractGateway;
import io.github.loadup.productcenter.domain.gateway.RequestKeyConflictException;
import io.github.loadup.productcenter.domain.model.CatalogEntry;
import io.github.loadup.productcenter.domain.model.CatalogKind;
import io.github.loadup.productcenter.domain.model.CatalogStatus;
import io.github.loadup.productcenter.domain.model.ContractRecord;
import io.github.loadup.productcenter.domain.model.ContractRevisionRecord;
import io.github.loadup.productcenter.domain.model.ContractSigner;
import io.github.loadup.productcenter.domain.model.ContractStatus;
import io.github.loadup.productcenter.domain.model.MerchantContractRevision;
import io.github.loadup.productcenter.domain.model.TypedValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MerchantContractService implements MerchantContractFacade {
    private final CatalogGateway catalog;
    private final MerchantContractGateway gateway;
    private final CatalogAssembler assembler;
    private final ContractCodec codec;
    private final ContractConverter converter;
    private final MerchantFacts facts;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public MerchantContractService(
            CatalogGateway catalog,
            MerchantContractGateway gateway,
            CatalogAssembler assembler,
            ContractCodec codec,
            ContractConverter converter,
            MerchantFacts facts,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.catalog = catalog;
        this.gateway = gateway;
        this.assembler = assembler;
        this.codec = codec;
        this.converter = converter;
        this.facts = facts;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public MerchantContractDTO preview(ContractSignCommand command, String actor) {
        validate(command, actor);
        var profile = facts.load(command.merchantId());
        var entry = catalog.find(command.planVersionId())
                .orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND));
        requirePlan(entry);
        Instant now = clock.instant();
        String id = UUID.randomUUID().toString();
        var snapshot = sign(id, entry, command, profile, now);
        return converter.toContract(header(id, command, "preview", actor, now), snapshot);
    }

    public MerchantContractDTO sign(ContractSignCommand command, String actor) {
        validate(command, actor);
        String digest = requestDigest(command);
        var existing = gateway.findByRequest(command.requestKey());
        if (existing.isPresent()) {
            return replay(existing.get(), digest);
        }
        // External merchant lookup completes before the transaction acquires catalog/database locks.
        var profile = facts.load(command.merchantId());
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                var entry = catalog.lock(command.planVersionId())
                        .orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND));
                // Recheck after the lock; a competing signer may have committed while we waited.
                var replay = gateway.findByRequest(command.requestKey());
                if (replay.isPresent()) {
                    return replay(replay.get(), digest);
                }
                requirePlan(entry);
                Instant now = clock.instant();
                String id = UUID.randomUUID().toString();
                var snapshot = sign(id, entry, command, profile, now);
                var header = header(id, command, digest, actor, now);
                var revision = new ContractRevisionRecord(
                        UUID.randomUUID().toString(),
                        id,
                        1,
                        codec.write(snapshot),
                        snapshot.snapshotHash(),
                        header.createdAt(),
                        header.createdAt());
                gateway.insert(header, revision);
                return converter.toContract(header, snapshot);
            }));
        } catch (RequestKeyConflictException e) {
            // Roll back both inserts before reading the winner in a fresh session.
            var winner = gateway.findByRequest(command.requestKey());
            if (winner.isPresent()) {
                return replay(winner.get(), digest);
            }
            throw new CommonException(ContractResultCodeEnum.CONFLICT, e);
        }
    }

    private void validate(ContractSignCommand command, String actor) {
        ContractIdentity.actor(actor);
        ContractIdentity.code(command.merchantId(), 64);
        ContractIdentity.code(command.scopeKey(), 128);
        ContractIdentity.code(command.requestKey(), 128);
        if (command.selectedItems() == null || command.values() == null) {
            throw new CommonException(ContractResultCodeEnum.SELECTION_AND_VALUES_REQUIRED);
        }
    }

    private void requirePlan(CatalogEntry entry) {
        if (entry.kind() != CatalogKind.SALES_PLAN || entry.status() != CatalogStatus.PUBLISHED) {
            throw new CommonException(ContractResultCodeEnum.PLAN_MUST_BE_PUBLISHED_AND_ON_SALE);
        }
    }

    private MerchantContractRevision sign(
            String id, CatalogEntry entry, ContractSignCommand command, Map<String, TypedValue> profile, Instant now) {
        var plan = assembler.plan(entry, false);
        return new ContractSigner()
                .sign(
                        command.merchantId(),
                        command.scopeKey(),
                        id,
                        1,
                        plan,
                        command.selectedItems(),
                        assembler.merchantValues(plan, command.values()),
                        profile,
                        now,
                        command.effectiveFrom() == null ? now : command.effectiveFrom(),
                        command.effectiveTo());
    }

    private ContractRecord header(String id, ContractSignCommand command, String digest, String actor, Instant now) {
        LocalDateTime at = LocalDateTime.ofInstant(now, clock.getZone());
        return new ContractRecord(
                id,
                command.merchantId(),
                command.scopeKey(),
                command.planVersionId(),
                ContractStatus.NORMAL,
                1,
                command.requestKey(),
                digest,
                actor,
                at,
                at);
    }

    private String requestDigest(ContractSignCommand command) {
        Map<String, Object> value = new HashMap<>();
        value.put("merchantId", command.merchantId());
        value.put("scopeKey", command.scopeKey());
        value.put("planVersionId", command.planVersionId());
        value.put("selectedItems", command.selectedItems());
        value.put("values", command.values());
        value.put(
                "effectiveFrom",
                command.effectiveFrom() == null ? null : command.effectiveFrom().toString());
        value.put(
                "effectiveTo",
                command.effectiveTo() == null ? null : command.effectiveTo().toString());
        return codec.digest(value);
    }

    private MerchantContractDTO replay(ContractRecord existing, String digest) {
        if (!MessageDigest.isEqual(
                existing.requestDigest().getBytes(StandardCharsets.UTF_8), digest.getBytes(StandardCharsets.UTF_8))) {
            throw new CommonException(ContractResultCodeEnum.IDEMPOTENCY_CONFLICT);
        }
        return toDTO(existing);
    }

    public MerchantContractRevision snapshot(ContractRecord header) {
        var stored = gateway.revision(header.id(), 1)
                .orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND));
        var snapshot = codec.read(stored.snapshot(), MerchantContractRevision.class);
        if (!snapshot.snapshotHash().equals(stored.snapshotHash())
                || !snapshot.merchantId().equals(header.merchantId())
                || !snapshot.scopeKey().equals(header.scopeKey())
                || !snapshot.contractId().equals(header.id())
                || snapshot.revision() != 1) {
            throw new CommonException(CommonResultCodeEnum.SYS_ERROR);
        }
        return snapshot;
    }

    private MerchantContractDTO toDTO(ContractRecord header) {
        return converter.toContract(header, snapshot(header));
    }

    @Transactional(readOnly = true)
    public MerchantContractDTO detail(String id) {
        return toDTO(gateway.find(id).orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public PageDTO<MerchantContractDTO> page(MerchantContractPageQuery query) {
        ContractIdentity.page(query.page(), query.size());
        var page = gateway.page(query.merchantId(), query.page(), query.size());
        return PageDTO.of(page.records().stream().map(this::toDTO).toList(), page.total(), query.page(), query.size());
    }

    @Transactional
    public MerchantContractDTO changeStatus(ContractStatusCommand command, String actor) {
        ContractIdentity.actor(actor);
        ContractStatus target = ContractStatus.valueOf(command.status());
        var header =
                gateway.find(command.id()).orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND));
        if (header.status() == ContractStatus.TERMINATED) {
            throw new CommonException(ContractResultCodeEnum.INVALID_STATE);
        }
        if (header.generation() != command.expectedGeneration()) {
            throw new CommonException(ContractResultCodeEnum.CONFLICT);
        }
        if (header.status() != target) {
            gateway.updateStatus(header.id(), header.generation(), target);
        }
        return detail(command.id());
    }
}
