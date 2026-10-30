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

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentAttemptStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentAttemptDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentAttemptMapper;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PaymentAttemptGatewayImpl extends ServiceImpl<PaymentAttemptMapper, PaymentAttemptDO>
        implements PaymentAttemptGateway {
    private static final String COLUMNS = "id, created_at, updated_at, payment_id, attempt_no, amount_minor, currency, "
            + "binding_revision_id, channel, access_mode, account_scope_hash, method, channel_request_no, state, execution_epoch, "
            + "no_collection_evidence_id, limit_operation_id, caller_service_id, request_key, request_digest, payment_request_digest, "
            + "prepared_input_ref, risk_decision, risk_reason, risk_consultation_ref, execution_permit_hash, prepared_at, submission_started_at, row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final ChannelBindingGateway bindings;
    private final PaymentAttemptStorageConverter converter;
    private final Clock clock;

    public PaymentAttemptGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            PaymentAttemptMapper mapper,
            ChannelBindingGateway bindings,
            PaymentAttemptStorageConverter converter,
            Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.bindings = bindings;
        this.converter = converter;
        this.clock = clock;
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }

    @Override
    public Optional<StoredPaymentAttempt> find(String tenant, String id) {
        scope(tenant);
        return read(tenant, getById(id));
    }

    @Override
    public Optional<StoredPaymentAttempt> findRequest(String tenant, String payment, String key) {
        scope(tenant);
        return read(
                tenant,
                getOne(QueryWrapper.create()
                        .eq("payment_id", payment)
                        .eq("request_key", key)));
    }

    @Override
    public Optional<StoredPaymentAttempt> findChannelRequest(String tenant, String bindingRevisionId,
                                                             String channelRequestNo) {
        scope(tenant);
        var args = new MapSqlParameterSource().addValue("binding", bindingRevisionId).addValue("request", channelRequestNo);
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM payment_attempt WHERE deleted = FALSE "
                        + "AND binding_revision_id = :binding AND channel_request_no = :request LIMIT 2", args,
                BeanPropertyRowMapper.newInstance(PaymentAttemptDO.class));
        if (rows.size() > 1) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return rows.isEmpty() ? Optional.empty() : read(tenant, rows.get(0));
    }

    @Override
    public Optional<StoredPaymentAttempt> lock(String tenant, String id) {
        requireTransaction(tenant);
        var args = new MapSqlParameterSource().addValue("id", id);
        return read(tenant, one("id = :id", args, true));
    }

    private PaymentAttemptDO one(String condition, MapSqlParameterSource args, boolean lock) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payment_attempt WHERE deleted = FALSE AND "
                        + condition + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(PaymentAttemptDO.class))
                .stream().findFirst().orElse(null);
    }

    private Optional<StoredPaymentAttempt> read(String tenant, PaymentAttemptDO row) {
        if (row == null) {
            return Optional.empty();
        }
        try {
            var binding = bindings.findRevision(tenant, row.getBindingRevisionId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!binding.channel().name().equals(row.getChannel()) || !binding.accessMode().name().equals(row.getAccessMode())
                    || !binding.accountScopeHash().equals(row.getAccountScopeHash())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var risk = row.getRiskDecision() == null ? null : converter.toRisk(row);
            if (risk == null && (row.getRiskReason() != null || row.getRiskConsultationRef() != null)) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return Optional.of(converter.toStored(row, converter.toAttempt(row, binding), risk));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, StoredPaymentAttempt value) {
        requireTransaction(tenant);
        if (value.attempt().state() != PaymentAttemptState.INIT || value.rowVersion() != 0) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_attempt (id, created_at, updated_at, deleted, payment_id, attempt_no,
                    amount_minor, currency, binding_revision_id, channel, access_mode, account_scope_hash, method,
                    channel_request_no, state, execution_epoch, no_collection_evidence_id, limit_operation_id,
                    caller_service_id, request_key, request_digest, payment_request_digest, prepared_input_ref,
                    risk_decision, risk_reason, risk_consultation_ref, execution_permit_hash, prepared_at, submission_started_at, row_version)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :paymentId, :attemptNo, :amountMinor, :currency,
                    :bindingRevisionId, :channel, :accessMode, :accountScopeHash, :method, :channelRequestNo, :state,
                    :executionEpoch, :noCollectionEvidenceId, :limitOperationId, :callerServiceId, :requestKey, :requestDigest,
                    :paymentRequestDigest, :preparedInputRef, :riskDecision, :riskReason, :riskConsultationRef,
                    :executionPermitHash, :preparedAt, :submissionStartedAt, :rowVersion)
                ON CONFLICT (payment_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Payment attempt request key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void update(String tenant, StoredPaymentAttempt value, long expectedVersion) {
        requireTransaction(tenant);
        if (value.rowVersion() != Math.incrementExact(expectedVersion)) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toTransitionPatch(value);
        var args = new MapSqlParameterSource().addValue("id", value.attempt().id())
                .addValue("payment", value.attempt().paymentId()).addValue("expected", expectedVersion)
                .addValue("state", row.getState()).addValue("epoch", row.getExecutionEpoch())
                .addValue("evidence", row.getNoCollectionEvidenceId()).addValue("decision", row.getRiskDecision())
                .addValue("reason", row.getRiskReason()).addValue("consultation", row.getRiskConsultationRef())
                .addValue("permit", row.getExecutionPermitHash()).addValue("started", row.getSubmissionStartedAt())
                .addValue("version", row.getRowVersion()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE payment_attempt SET state = :state, execution_epoch = :epoch, no_collection_evidence_id = :evidence,
                    risk_decision = :decision, risk_reason = :reason, risk_consultation_ref = :consultation,
                    execution_permit_hash = :permit, submission_started_at = :started, row_version = :version, updated_at = :updated
                WHERE id = :id AND payment_id = :payment AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void requireTransaction(String tenant) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
