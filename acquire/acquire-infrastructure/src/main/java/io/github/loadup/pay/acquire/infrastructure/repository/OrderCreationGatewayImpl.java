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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderFee;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderCreationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AcquireOrderDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderContractSnapshotDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderCreateIntentDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class OrderCreationGatewayImpl implements OrderCreationGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final OrderCreationStorageConverter converter;
    private final AcquireOrderConverter business;
    private final OrderCreationCodec codec;

    public OrderCreationGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            OrderCreationStorageConverter converter,
            AcquireOrderConverter business,
            OrderCreationCodec codec, @Qualifier("acquireClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.business = business;
        this.codec = codec;
    }

    private OrderCreateIntentDO intent(String tenant, String predicate, MapSqlParameterSource args, boolean lock) {
        return jdbc.query("""
                        SELECT id, tenant_id, created_at, updated_at, payee_merchant_id, merchant_order_no, order_id, request_digest,
                               caller_scope_hash, entry_mode, intent_payload, state, admission_id, materialize_before, result_code,
                               row_version, initial_session_id FROM order_create_intent WHERE tenant_id = :tenant AND deleted = FALSE AND
                        """ + predicate + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(OrderCreateIntentDO.class))
                .stream().findFirst().orElse(null);
    }

    @Override
    public Optional<OrderCreateIntent> findIntent(String tenant, String payee, String merchantOrderNo) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("payee", payee).addValue("number", merchantOrderNo);
        return Optional.ofNullable(intent(tenant, "payee_merchant_id = :payee AND merchant_order_no = :number", args, false))
                .map(converter::toIntent);
    }

    @Override
    public OrderCreateIntent lockIntent(String tenant, String id) {
        requireTransaction();
        var row = intent(tenant, "id = :id", new MapSqlParameterSource("tenant", tenant).addValue("id", id), true);
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        return converter.toIntent(row);
    }

    @Override
    public void insertIntent(String tenant, OrderCreateIntent intent) {
        requireTransaction();
        var row = converter.toIntentDO(tenant, intent);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO order_create_intent (id, tenant_id, created_at, updated_at, deleted, payee_merchant_id, merchant_order_no,
                    order_id, request_digest, caller_scope_hash, entry_mode, intent_payload, state, admission_id, materialize_before,
                    result_code, row_version, initial_session_id)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :payeeMerchantId, :merchantOrderNo, :orderId,
                    :requestDigest, :callerScopeHash, :entryMode, :intentPayload, :state, :admissionId, :materializeBefore,
                    :resultCode, :rowVersion, :initialSessionId)
                ON CONFLICT (tenant_id, payee_merchant_id, merchant_order_no) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new AcquireWriteConflictException("Order creation request already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateIntent(String tenant, OrderCreateIntent value, long expectedVersion) {
        requireTransaction();
        var row = converter.toIntentDO(tenant, value);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", value.id()).addValue("expected", expectedVersion)
                .addValue("state", row.getState()).addValue("admission", row.getAdmissionId())
                .addValue("deadline", row.getMaterializeBefore()).addValue("result", row.getResultCode())
                .addValue("version", row.getRowVersion()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE order_create_intent SET state = :state, admission_id = COALESCE(:admission, admission_id),
                    materialize_before = COALESCE(:deadline, materialize_before), result_code = COALESCE(:result, result_code),
                    row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND state = 'INIT' AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public Optional<StoredAcquireOrder> findOrder(String tenant, String id) {
        var row = order(tenant, id, false);
        if (row == null) return Optional.empty();
        try {
            var intentRow = intent(tenant, "order_id = :order", new MapSqlParameterSource("tenant", tenant).addValue("order", id), false);
            var snapshotArgs = new MapSqlParameterSource("tenant", tenant).addValue("id", row.getContractSnapshotId()).addValue("order", id);
            var snapshot = jdbc.query("""
                    SELECT id, tenant_id, created_at, updated_at, order_id, contract_id, contract_revision, contract_generation,
                           snapshot_hash, compiled_terms_hash, schema_version, configuration, payload_hash, admission_digest
                    FROM order_contract_snapshot WHERE tenant_id = :tenant AND id = :id AND order_id = :order AND deleted = FALSE
                    """, snapshotArgs, BeanPropertyRowMapper.newInstance(OrderContractSnapshotDO.class)).stream().findFirst().orElse(null);
            if (intentRow == null || snapshot == null || !"COMPLETED".equals(intentRow.getState()))
                throw new CommonException(SNAPSHOT_INVALID);
            var intent = converter.toIntent(intentRow);
            final io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand command;
            final String digest;
            switch (intent.entryMode()) {
                case "ORDER" -> {
                    command = codec.decodeIntent(intent);
                    digest = codec.requestDigest(tenant, intent.callerScopeHash(), command);
                }
                case "CHECKOUT" -> {
                    var checkout = codec.decodeCheckout(intent);
                    command = business.toOrderCreate(checkout);
                    digest = codec.checkoutDigest(tenant, intent.callerScopeHash(), checkout);
                }
                case "DIRECT_PAY" -> {
                    var direct = codec.decodeDirectPay(intent);
                    command = business.toOrderCreate(direct);
                    digest = codec.directPayDigest(tenant, intent.callerScopeHash(), direct);
                }
                default -> throw new CommonException(SNAPSHOT_INVALID);
            }
            var admission = codec.decodeAdmission(snapshot.getConfiguration(), snapshot.getPayloadHash());
            if (!digest.equals(intent.requestDigest())
                    || !id.equals(admission.orderId())
                    || !row.getPayeeMerchantId().equals(admission.payeeMerchantId())
                    || !row.getPayeeMerchantId().equals(command.payeeMerchantId())
                    || !row.getMerchantOrderNo().equals(command.merchantOrderNo())
                    || !row.getSubject().equals(command.subject())
                    || row.getAmountMinor() != command.amountMinor()
                    || !row.getCurrency().equals(command.currency())
                    || !row.getAdmissionId().equals(admission.admissionId())
                    || !intent.admissionId().equals(admission.admissionId())
                    || !intent.requestDigest().equals(admission.requestDigest())
                    || !row.getAdmittedAt().equals(BusinessTime.local(admission.admittedAt()))
                    || !Objects.equals(snapshot.getContractId(), admission.contractId())
                    || !Objects.equals(snapshot.getContractRevision(), admission.revision())
                    || !Objects.equals(snapshot.getContractGeneration(), admission.generation())
                    || !Objects.equals(snapshot.getSnapshotHash(), admission.snapshotHash())
                    || !Objects.equals(snapshot.getCompiledTermsHash(), admission.compiledTermsHash())
                    || !Objects.equals(
                    snapshot.getSchemaVersion(), admission.terms().schemaVersion())
                    || !Objects.equals(snapshot.getAdmissionDigest(), admission.requestDigest()))
                throw new CommonException(SNAPSHOT_INVALID);
            var policy = business.toPaymentPolicy(admission.terms().payment());
            var order = converter.toOrder(
                    row,
                    policy,
                    business.toUnpaidAction(admission.terms().order().unpaidAction()));
            return Optional.of(converter.toStored(row, snapshot, intent, order, business.toProof(admission)));
        } catch (RuntimeException corrupted) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public StoredAcquireOrder lockOrder(String tenant, String id) {
        requireTransaction();
        var row = order(tenant, id, true);
        if (row == null) throw new CommonException(NOT_FOUND);
        // The held header lock serializes new sessions with future order lifecycle writes.
        return findOrder(tenant, id).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
    }

    @Override
    public void insertOrder(String tenant, StoredAcquireOrder value, OrderFee fee, String feePayload) {
        requireTransaction();
        if (!value.order().id().equals(fee.orderId()) || !value.order().amount().equals(fee.baseAmount()))
            throw new CommonException(SNAPSHOT_INVALID);
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var now = LocalDateTime.now(clock);
        var snapshot = converter.toSnapshotDO(tenant, value);
        snapshot.setCreatedAt(now);
        snapshot.setUpdatedAt(now);
        int snapshotCount = jdbc.update("""
                INSERT INTO order_contract_snapshot (id, tenant_id, created_at, updated_at, deleted, order_id, contract_id,
                    contract_revision, contract_generation, snapshot_hash, compiled_terms_hash, schema_version, configuration,
                    payload_hash, admission_digest)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :contractId, :contractRevision,
                    :contractGeneration, :snapshotHash, :compiledTermsHash, :schemaVersion, :configuration, :payloadHash, :admissionDigest)
                """, new BeanPropertySqlParameterSource(snapshot));
        if (snapshotCount != 1) throw new CommonException(CONCURRENT_UPDATE);
        var row = converter.toOrderDO(tenant, value);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO acquire_order (id, tenant_id, created_at, updated_at, deleted, payee_merchant_id, merchant_order_no,
                    admission_id, contract_snapshot_id, subject, currency, amount_minor, paid_amount_minor, payment_reserved_minor,
                    successful_payment_count, reserved_payment_count, state, admitted_at, order_due_at, deadline_version, close_epoch,
                    paid_at, closed_at, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :payeeMerchantId, :merchantOrderNo, :admissionId,
                    :contractSnapshotId, :subject, :currency, :amountMinor, :paidAmountMinor, :paymentReservedMinor,
                    :successfulPaymentCount, :reservedPaymentCount, :state, :admittedAt, :orderDueAt, :deadlineVersion, :closeEpoch,
                    :paidAt, :closedAt, :rowVersion)
                """, new BeanPropertySqlParameterSource(row));
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
        var feeRow = converter.toFeeDO(tenant, fee, feePayload);
        feeRow.setCreatedAt(now);
        feeRow.setUpdatedAt(now);
        int feeCount = jdbc.update("""
                INSERT INTO order_fee (id, tenant_id, created_at, updated_at, deleted, order_id, fee_type, currency,
                    base_amount_minor, fee_amount_minor, fee_rule_snapshot, refund_basis_version, refund_basis_minor,
                    adjusted_amount_minor, state, confirmed_at, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :feeType, :currency, :baseAmountMinor,
                    :feeAmountMinor, :feeRuleSnapshot, :refundBasisVersion, :refundBasisMinor, :adjustedAmountMinor,
                    :state, :confirmedAt, :rowVersion)
                """, new BeanPropertySqlParameterSource(feeRow));
        if (feeCount != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private AcquireOrderDO order(String tenant, String id, boolean lock) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", id);
        return jdbc.query("""
                        SELECT id, tenant_id, created_at, updated_at, payee_merchant_id, merchant_order_no, admission_id,
                               contract_snapshot_id, subject, currency, amount_minor, paid_amount_minor, payment_reserved_minor,
                               successful_payment_count, reserved_payment_count, state, admitted_at, order_due_at, deadline_version,
                               close_epoch, paid_at, closed_at, row_version FROM acquire_order
                        WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE
                        """ + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(AcquireOrderDO.class))
                .stream().findFirst().orElse(null);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
