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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInputState;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentInputStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentInputDO;
import io.github.loadup.components.database.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class PaymentInputGatewayImpl implements PaymentInputGateway {
    private static final String COLUMNS = "id, tenant_id, created_at, updated_at, payment_id, order_id, allocation_id, "
            + "payee_merchant_id, amount_minor, currency, input_key, channel, method, caller_service_id, fingerprint_key_id, "
            + "input_digest, encryption_key, encryption_version, ciphertext, prepared_at, expires_at, state, bound_attempt_id, row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentInputRepository inputs;
    private final PaymentInputStorageConverter converter;
    private final Clock clock;

    public PaymentInputGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            PaymentInputRepository inputs,
            PaymentInputStorageConverter converter,
            @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.inputs = inputs;
        this.converter = converter;
        this.clock = clock;
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    @Override
    public Optional<PaymentInput> findByPayment(String tenant, String paymentId) {
        scope(tenant);
        return read(inputs.findByPayment(paymentId).orElse(null));
    }

    @Override
    public Optional<PaymentInput> find(String tenant, String inputRef) {
        return input(tenant, inputRef, false);
    }

    @Override
    public Optional<PaymentInput> lock(String tenant, String inputRef) {
        requireTransaction(tenant);
        return input(tenant, inputRef, true);
    }

    private Optional<PaymentInput> input(String tenant, String inputRef, boolean lock) {
        scope(tenant);
        if (!lock) {
            return read(inputs.findById(inputRef).orElse(null));
        }
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", inputRef);
        return read(jdbc.query("SELECT " + COLUMNS + " FROM payment_input WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE"
                        + " FOR UPDATE", args, BeanPropertyRowMapper.newInstance(PaymentInputDO.class))
                .stream().findFirst().orElse(null));
    }

    private Optional<PaymentInput> read(PaymentInputDO row) {
        try {
            return Optional.ofNullable(row).map(converter::toInput);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, PaymentInput value) {
        requireTransaction(tenant);
        if (value.state() != PaymentInputState.INIT || value.rowVersion() != 0)
            throw new CommonException(INVALID_STATE);
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_input (id, tenant_id, created_at, updated_at, deleted, payment_id, order_id,
                    allocation_id, payee_merchant_id, amount_minor, currency, input_key, channel, method, caller_service_id,
                    fingerprint_key_id, input_digest, encryption_key, encryption_version, ciphertext, prepared_at, expires_at,
                    state, bound_attempt_id, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :paymentId, :orderId, :allocationId,
                    :payeeMerchantId, :amountMinor, :currency, :inputKey, :channel, :method, :callerServiceId,
                    :fingerprintKeyId, :inputDigest, :encryptionKey, :encryptionVersion, :ciphertext, :preparedAt, :expiresAt,
                    :state, :boundAttemptId, :rowVersion)
                ON CONFLICT (tenant_id, payment_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new PaymentWriteConflictException("Payment input business key already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void bind(String tenant, PaymentInput value, long expectedVersion) {
        requireTransaction(tenant);
        if (expectedVersion != 0 || value.rowVersion() != 1 || value.state() != PaymentInputState.BOUND)
            throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", value.id()).addValue("payment", value.paymentId())
                .addValue("attempt", value.boundAttemptId()).addValue("expected", expectedVersion).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE payment_input SET state = 'BOUND', bound_attempt_id = :attempt, row_version = 1, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND payment_id = :payment AND state = 'INIT'
                    AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void requireTransaction(String tenant) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
