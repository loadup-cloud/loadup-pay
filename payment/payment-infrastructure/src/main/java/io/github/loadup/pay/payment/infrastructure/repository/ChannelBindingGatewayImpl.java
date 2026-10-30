/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.payment.app.port.ChannelBindingCodec;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingManagementGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingChangeReceipt;
import io.github.loadup.pay.payment.domain.model.ChannelBindingState;
import io.github.loadup.pay.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.pay.payment.domain.model.RoutableChannelBinding;
import io.github.loadup.pay.payment.domain.model.StoredChannelBindingRevision;
import io.github.loadup.pay.payment.infrastructure.converter.ChannelBindingStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelBindingChangeReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelBindingRevisionDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelMerchantBindingDO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class ChannelBindingGatewayImpl implements ChannelBindingManagementGateway, ChannelBindingGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ChannelBindingStorageConverter converter;
    private final ChannelBindingCodec codec;

    public ChannelBindingGatewayImpl(NamedParameterJdbcTemplate jdbc,
                                     ChannelBindingStorageConverter converter, ChannelBindingCodec codec,
                                     Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.codec = codec;
    }

    private ChannelMerchantBindingDO headRow(String tenant, String payee, String code, boolean lock) {
        var args = new MapSqlParameterSource().addValue("payee", payee).addValue("code", code);
        return jdbc.query("""
                        SELECT id, created_at, updated_at, payee_merchant_id, binding_code, channel, access_mode,
                               state, current_revision_id, current_revision, priority, row_version
                        FROM channel_merchant_binding WHERE payee_merchant_id = :payee
                            AND binding_code = :code AND deleted = FALSE
                        """ + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(ChannelMerchantBindingDO.class))
                .stream().findFirst().orElse(null);
    }

    @Override
    public Optional<ChannelMerchantBinding> find(String tenant, String payee, String code) {
        return head(headRow(tenant, payee, code, false));
    }

    @Override
    public Optional<ChannelMerchantBinding> lock(String tenant, String payee, String code) {
        requireTransaction();
        return head(headRow(tenant, payee, code, true));
    }

    private Optional<ChannelMerchantBinding> head(ChannelMerchantBindingDO row) {
        try {
            return Optional.ofNullable(row).map(converter::toHead);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<StoredChannelBindingRevision> revision(String tenant, String revisionId) {
        if (revisionId == null) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var args = new MapSqlParameterSource().addValue("id", revisionId);
        var row = jdbc.query("""
                SELECT id, created_at, updated_at, binding_id, revision, payee_merchant_id, channel, access_mode,
                       account_scope_hash, credential_reference, credential_version, identity_payload, identity_hash,
                       payment_code_enabled, jsapi_enabled, h5_enabled, caller_service_id, published_at
                FROM channel_binding_revision WHERE id = :id AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(ChannelBindingRevisionDO.class)).stream().findFirst().orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        try {
            var identity = codec.decodeIdentity(row.getIdentityPayload(), row.getIdentityHash());
            var capabilities = EnumSet.noneOf(PaymentMethod.class);
            capability(capabilities, PaymentMethod.PAYMENT_CODE, row.getPaymentCodeEnabled());
            capability(capabilities, PaymentMethod.JSAPI, row.getJsapiEnabled());
            capability(capabilities, PaymentMethod.H5, row.getH5Enabled());
            var binding = converter.toBinding(row, identity, capabilities);
            if (!binding.accountScopeHash().equals(codec.accountScopeHash(binding.channel(), identity))) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return Optional.of(converter.toRevision(row, binding));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void capability(Set<PaymentMethod> methods, PaymentMethod method, Boolean enabled) {
        if (enabled == null) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (enabled) {
            methods.add(method);
        }
    }

    @Override
    public Optional<ChannelBindingRevision> findRevision(String tenant, String revisionId) {
        return revision(tenant, revisionId).map(StoredChannelBindingRevision::binding);
    }

    @Override
    public List<RoutableChannelBinding> lockActive(String tenant, String payee, Channel channel,
                                                   Set<AccessMode> allowedModes, PaymentMethod method) {
        requireTransaction();
        if (allowedModes.isEmpty()) {
            return List.of();
        }
        var args = new MapSqlParameterSource().addValue("payee", payee)
                .addValue("channel", channel.name()).addValue("modes", allowedModes.stream().map(Enum::name).toList());
        var rows = jdbc.query("""
                SELECT id, created_at, updated_at, payee_merchant_id, binding_code, channel, access_mode,
                       state, current_revision_id, current_revision, priority, row_version
                FROM channel_merchant_binding WHERE payee_merchant_id = :payee
                    AND channel = :channel AND access_mode IN (:modes) AND state = 'ACTIVE' AND deleted = FALSE
                ORDER BY id ASC FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(ChannelMerchantBindingDO.class));
        var candidates = new ArrayList<RoutableChannelBinding>();
        for (var row : rows) {
            var head = head(row).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var revision = revision(tenant, head.currentRevisionId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            final RoutableChannelBinding candidate;
            try {
                candidate = new RoutableChannelBinding(head, revision);
            } catch (RuntimeException corrupt) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            if (revision.binding().capabilities().contains(method)) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingInt((RoutableChannelBinding candidate) -> candidate.head().priority())
                .thenComparing(candidate -> candidate.head().id()));
        return List.copyOf(candidates);
    }

    @Override
    public Optional<ChannelBindingChangeReceipt> receipt(String tenant, String bindingId, String key) {
        var args = new MapSqlParameterSource().addValue("binding", bindingId).addValue("key", key);
        var row = jdbc.query("""
                SELECT id, created_at, updated_at, binding_id, kind, request_key, request_digest, caller_service_id,
                       binding_revision_id, revision, state, applied_version, applied_at
                FROM channel_binding_change_receipt WHERE binding_id = :binding
                    AND request_key = :key AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(ChannelBindingChangeReceiptDO.class)).stream().findFirst().orElse(null);
        try {
            return Optional.ofNullable(row).map(converter::toReceipt);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertHead(String tenant, ChannelMerchantBinding head) {
        requireTransaction();
        if (head.state() != ChannelBindingState.ACTIVE || head.rowVersion() != 1 || head.currentRevision() != 1) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toHeadDO(tenant, head);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO channel_merchant_binding (id, created_at, updated_at, deleted, payee_merchant_id,
                    binding_code, channel, access_mode, state, current_revision_id, current_revision, priority, row_version)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :payeeMerchantId, :bindingCode, :channel,
                    :accessMode, :state, :currentRevisionId, :currentRevision, :priority, :rowVersion)
                ON CONFLICT (payee_merchant_id, binding_code) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Channel binding business key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void updateHead(String tenant, ChannelMerchantBinding head, long expectedVersion) {
        requireTransaction();
        if (head.rowVersion() != Math.incrementExact(expectedVersion) || head.state() == ChannelBindingState.INIT) {
            throw new CommonException(INVALID_STATE);
        }
        var patch = converter.toHeadPatch(head);
        var args = new MapSqlParameterSource().addValue("payee", head.payeeMerchantId())
                .addValue("code", head.bindingCode()).addValue("id", head.id()).addValue("channel", head.channel().name())
                .addValue("mode", head.accessMode().name()).addValue("expectedVersion", expectedVersion)
                .addValue("state", patch.getState()).addValue("revisionId", patch.getCurrentRevisionId())
                .addValue("revision", patch.getCurrentRevision()).addValue("priority", patch.getPriority())
                .addValue("version", patch.getRowVersion()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE channel_merchant_binding SET state = :state, current_revision_id = :revisionId,
                    current_revision = :revision, priority = :priority, row_version = :version, updated_at = :updated
                WHERE payee_merchant_id = :payee AND binding_code = :code AND id = :id
                    AND channel = :channel AND access_mode = :mode AND row_version = :expectedVersion AND deleted = FALSE
                """, args);
        if (changed != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void insertRevision(String tenant, StoredChannelBindingRevision value) {
        requireTransaction();
        String payload = codec.encodeIdentity(value.binding().identity());
        if (!codec.checksum(payload).equals(value.identityHash())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var row = converter.toRevisionDO(tenant, value, payload);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO channel_binding_revision (id, created_at, updated_at, deleted, binding_id, revision,
                    payee_merchant_id, channel, access_mode, account_scope_hash, credential_reference, credential_version,
                    identity_payload, identity_hash, payment_code_enabled, jsapi_enabled, h5_enabled, caller_service_id, published_at)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :bindingId, :revision, :payeeMerchantId, :channel,
                    :accessMode, :accountScopeHash, :credentialReference, :credentialVersion, :identityPayload, :identityHash,
                    :paymentCodeEnabled, :jsapiEnabled, :h5Enabled, :callerServiceId, :publishedAt)
                ON CONFLICT (binding_id, revision) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Channel binding revision already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void insertReceipt(String tenant, ChannelBindingChangeReceipt receipt) {
        requireTransaction();
        var row = converter.toReceiptDO(tenant, receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO channel_binding_change_receipt (id, created_at, updated_at, deleted, binding_id, kind,
                    request_key, request_digest, caller_service_id, binding_revision_id, revision, state, applied_version, applied_at)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :bindingId, :kind, :requestKey, :requestDigest,
                    :callerServiceId, :bindingRevisionId, :revision, :state, :appliedVersion, :appliedAt)
                ON CONFLICT (binding_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Channel binding change already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
