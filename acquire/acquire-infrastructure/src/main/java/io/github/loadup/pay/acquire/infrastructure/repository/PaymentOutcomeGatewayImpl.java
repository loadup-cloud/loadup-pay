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
package io.github.loadup.pay.acquire.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentChange;
import io.github.loadup.pay.acquire.domain.model.PaymentOutcomeReceipt;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.VerifiedPaymentOutcome;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentInitiationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentOutcomeStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderPaymentAllocationDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderPaymentOutcomeDO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PaymentOutcomeGatewayImpl implements PaymentOutcomeGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final PaymentInitiationStorageConverter allocationConverter;
    private final PaymentOutcomeStorageConverter converter;
    private final PaymentInitiationCodec codec;

    public PaymentOutcomeGatewayImpl(NamedParameterJdbcTemplate jdbc, PaymentInitiationStorageConverter allocationConverter,
                                     PaymentOutcomeStorageConverter converter, PaymentInitiationCodec codec, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.allocationConverter = allocationConverter;
        this.converter = converter;
        this.codec = codec;
    }

    @Override
    public Optional<StoredPaymentAllocation> findPayment(String tenant, String orderId, String paymentId) {
        return readAllocation(tenant, orderId, paymentId, false);
    }

    @Override
    public Optional<StoredPaymentAllocation> lockPayment(String tenant, String orderId, String paymentId) {
        requireTransaction();
        return readAllocation(tenant, orderId, paymentId, true);
    }

    private Optional<StoredPaymentAllocation> readAllocation(String tenant, String orderId, String paymentId, boolean lock) {
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("order", orderId).addValue("payment", paymentId);
        var row = jdbc.query("""
                        SELECT id, tenant_id, created_at, updated_at, order_id, payment_id, actor_scope, action_key, request_digest,
                               caller_scope_hash, amount_minor, currency, state, payment_source_version, intent_payload, payload_hash, row_version
                        FROM order_payment_allocation WHERE tenant_id = :tenant AND order_id = :order AND payment_id = :payment AND deleted = FALSE
                        """ + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(OrderPaymentAllocationDO.class))
                .stream().findFirst().orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        try {
            var value = allocationConverter.toStored(row, allocationConverter.toAllocation(row));
            codec.decode(value);
            return Optional.of(value);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<PaymentOutcomeReceipt> findReceipt(String tenant, String allocationId) {
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("allocation", allocationId);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, allocation_id, order_id, payment_id, amount_minor, currency,
                       outcome_kind, payment_source_version, evidence_id, execution_fenced, observed_at
                FROM order_payment_outcome WHERE tenant_id = :tenant AND allocation_id = :allocation AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(OrderPaymentOutcomeDO.class)).stream().findFirst().orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(converter.toReceipt(row, converter.toOutcome(row)));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void apply(String tenant, OrderPaymentChange change, StoredPaymentAllocation original,
                      PaymentOutcomeReceipt receipt, long expectedOrderVersion) {
        requireTransaction();
        var order = change.order();
        var allocation = change.allocation();
        receipt.requireBoundTo(allocation);
        var expectedState = receipt.outcome().kind() == VerifiedPaymentOutcome.Kind.SUCCEEDED
                ? AllocationState.CONSUMED : AllocationState.RELEASED;
        if (original.allocation().state() != AllocationState.HELD
                || !original.allocation().transition(expectedState, receipt.outcome().paymentVersion()).equals(allocation)
                || (receipt.outcome().kind() == VerifiedPaymentOutcome.Kind.NOT_ACCEPTED
                ? original.allocation().paymentSourceVersion() != 0
                : receipt.outcome().paymentVersion() <= original.allocation().paymentSourceVersion())
                || order.version() != Math.incrementExact(expectedOrderVersion)
                || !(order.state() == AcquireOrderState.PENDING || order.state() == AcquireOrderState.CLOSING || order.state() == AcquireOrderState.PAID)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var orderPatch = converter.toOrderPatch(order, order.state() == AcquireOrderState.PAID ? receipt.observedAt() : null);
        var orderArgs = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", order.id()).addValue("expected", expectedOrderVersion)
                .addValue("paid", orderPatch.getPaidAmountMinor()).addValue("reserved", orderPatch.getPaymentReservedMinor())
                .addValue("successCount", orderPatch.getSuccessfulPaymentCount()).addValue("reservedCount", orderPatch.getReservedPaymentCount())
                .addValue("state", orderPatch.getState()).addValue("version", orderPatch.getRowVersion()).addValue("paidAt", orderPatch.getPaidAt())
                .addValue("updated", LocalDateTime.now(clock));
        int orderChanged = jdbc.update("""
                UPDATE acquire_order SET paid_amount_minor = :paid, payment_reserved_minor = :reserved,
                    successful_payment_count = :successCount, reserved_payment_count = :reservedCount, state = :state,
                    row_version = :version, paid_at = COALESCE(:paidAt, paid_at), updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND state IN ('PENDING', 'CLOSING') AND deleted = FALSE
                """, orderArgs);
        if (orderChanged != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        var patch = converter.toAllocationPatch(allocation, Math.incrementExact(original.rowVersion()));
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", allocation.id()).addValue("order", order.id())
                .addValue("payment", allocation.paymentId()).addValue("expected", original.rowVersion())
                .addValue("source", original.allocation().paymentSourceVersion()).addValue("state", patch.getState())
                .addValue("newSource", patch.getPaymentSourceVersion()).addValue("version", patch.getRowVersion())
                .addValue("updated", LocalDateTime.now(clock));
        int allocationChanged = jdbc.update("""
                UPDATE order_payment_allocation SET state = :state, payment_source_version = :newSource,
                    row_version = :version, updated_at = :updated WHERE tenant_id = :tenant AND id = :id AND order_id = :order
                    AND payment_id = :payment AND state = 'HELD' AND row_version = :expected AND payment_source_version = :source AND deleted = FALSE
                """, args);
        if (allocationChanged != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        var row = converter.toDO(tenant, receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO order_payment_outcome (id, tenant_id, created_at, updated_at, deleted, allocation_id, order_id,
                    payment_id, amount_minor, currency, outcome_kind, payment_source_version, evidence_id, execution_fenced, observed_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :allocationId, :orderId, :paymentId, :amountMinor,
                    :currency, :outcomeKind, :paymentSourceVersion, :evidenceId, :executionFenced, :observedAt)
                ON CONFLICT (tenant_id, allocation_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new AcquireWriteConflictException("Payment outcome allocation already exists");
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
