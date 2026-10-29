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
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.domain.gateway.RefundGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundCapacityReservation;
import io.github.loadup.pay.payment.domain.model.RefundRequestReference;
import io.github.loadup.pay.payment.domain.model.RefundRequestState;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentResultStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.RefundStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAllocationPlanDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundRequestDO;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class RefundGatewayImpl implements RefundGateway {
    private static final String REQUEST_COLUMNS = "id, tenant_id, created_at, updated_at, order_id, payee_merchant_id, merchant_refund_no, "
            + "reason_code, request_digest, caller_subject_hash, admission_id, amount_minor, currency, allocation_policy, plan_version, "
            + "state, succeeded_minor, failed_minor, unknown_count, refund_terms, terms_hash, planned_at, row_version";
    private static final String CHILD_COLUMNS = "id, tenant_id, created_at, updated_at, refund_request_id, payment_id, original_attempt_id, "
            + "binding_revision_id, channel, account_scope_hash, original_channel_request_no, original_channel_transaction_no, "
            + "original_succeeded_at, submission_started_at, channel_refund_no, channel_refund_transaction_no, amount_minor, currency, "
            + "state, first_unknown_at, terminal_evidence_id, next_query_at, query_sequence, succeeded_at, row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final RefundStorageConverter converter;
    private final PaymentResultStorageConverter capacityConverter;
    private final PaymentAcceptanceCodec codec;
    private final JsonMapper json;

    public RefundGatewayImpl(NamedParameterJdbcTemplate jdbc, RefundStorageConverter converter, PaymentResultStorageConverter capacityConverter,
                             PaymentAcceptanceCodec codec, JsonMapper json, @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.capacityConverter = capacityConverter;
        this.codec = codec;
        this.json = json.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    @Override
    public Optional<RefundRequestReference> reference(String tenant, String id) {
        requireReadTransaction(tenant);
        requireJdbc();
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", id);
        return read(() -> Optional.ofNullable(request("id = :id", args, false)).map(converter::toReference));
    }

    @Override
    public Optional<RefundRequestReference> merchantReference(String tenant, String merchant, String number) {
        requireReadTransaction(tenant);
        requireJdbc();
        var args = new MapSqlParameterSource("tenant", tenant).addValue("merchant", merchant).addValue("number", number);
        return read(() -> Optional.ofNullable(request("payee_merchant_id = :merchant AND merchant_refund_no = :number", args, false)).map(converter::toReference));
    }

    @Override
    public StoredRefundRequest lock(String tenant, String id) {
        requireTransaction(tenant);
        requireJdbc();
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", id);
        if (request("id = :id", args, true) == null)
            throw new CommonException(NOT_FOUND);
        return load(tenant, id);
    }

    @Override
    public StoredRefundRequest load(String tenant, String id) {
        requireReadTransaction(tenant);
        requireJdbc();
        return read(() -> {
            var row = request("id = :id", new MapSqlParameterSource("tenant", tenant).addValue("id", id), false);
            if (row == null) throw new CommonException(NOT_FOUND);
            if (!"FIFO".equals(row.getAllocationPolicy()) || row.getPlanVersion() != 1
                    || !codec.checksum(row.getRefundTerms()).equals(row.getTermsHash()))
                throw new CommonException(SNAPSHOT_INVALID);
            var terms = json.readValue(row.getRefundTerms(), AdmittedOrderTermsDTO.class);
            if (!row.getAdmissionId().equals(terms.admissionId()) || !row.getOrderId().equals(terms.orderId())
                    || !row.getPayeeMerchantId().equals(terms.payeeMerchantId()) || !row.getCurrency().equals(terms.currency()))
                throw new CommonException(SNAPSHOT_INVALID);
            var args = new MapSqlParameterSource("tenant", tenant).addValue("request", id);
            var fixed = jdbc.query("""
                    SELECT id, tenant_id, created_at, updated_at, refund_request_id, plan_version, sequence_no,
                           payment_id, refund_id, amount_minor FROM refund_allocation_plan
                    WHERE tenant_id = :tenant AND refund_request_id = :request AND deleted = FALSE ORDER BY sequence_no ASC
                    """, args, BeanPropertyRowMapper.newInstance(RefundAllocationPlanDO.class));
            var rows = jdbc.query("SELECT " + CHILD_COLUMNS + " FROM refund_order WHERE tenant_id = :tenant "
                            + "AND refund_request_id = :request AND deleted = FALSE", args,
                    BeanPropertyRowMapper.newInstance(RefundOrderDO.class));
            var byId = new HashMap<String, RefundOrderDO>();
            rows.forEach(child -> byId.put(child.getId(), child));
            if (fixed.isEmpty() || fixed.size() != rows.size() || byId.size() != rows.size())
                throw new CommonException(SNAPSHOT_INVALID);
            var allocations = new ArrayList<StoredRefundChild>();
            for (var item : fixed) {
                var child = byId.remove(item.getRefundId());
                if (child == null || !child.getRefundRequestId().equals(id) || !child.getPaymentId().equals(item.getPaymentId())
                        || !child.getAmountMinor().equals(item.getAmountMinor()) || !item.getPlanVersion().equals(row.getPlanVersion())
                        || !child.getCurrency().equals(row.getCurrency()) || item.getSequenceNo() != allocations.size() + 1)
                    throw new CommonException(SNAPSHOT_INVALID);
                allocations.add(converter.toChild(child, converter.toRefund(child), converter.toIdentity(child), item.getSequenceNo()));
            }
            var request = converter.toRequest(row, allocations.stream().map(StoredRefundChild::refund).toList());
            if (request.succeededMinor() != row.getSucceededMinor() || request.failedMinor() != row.getFailedMinor()
                    || request.unknownCount() != row.getUnknownCount()) throw new CommonException(SNAPSHOT_INVALID);
            return converter.toStored(row, request, allocations);
        });
    }

    @Override
    public List<PaymentRefundCapacity> lockCapacities(String tenant, String orderId) {
        requireTransaction(tenant);
        requireJdbc();
        var args = new MapSqlParameterSource("tenant", tenant).addValue("order", orderId);
        return read(() -> jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, payment_id, order_id, payee_merchant_id,
                       succeeded_at, succeeded_minor, currency, refunded_minor, reserved_minor, row_version
                FROM payment_refund_capacity WHERE tenant_id = :tenant AND order_id = :order AND deleted = FALSE
                ORDER BY payment_id ASC FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(PaymentRefundCapacityDO.class)).stream().map(capacityConverter::toCapacity).toList());
    }

    @Override
    public void insertPlan(String tenant, StoredRefundRequest value, List<RefundCapacityReservation> reservations) {
        requireTransaction(tenant);
        requireJdbc();
        if (value.request().state() != RefundRequestState.INIT || value.request().version() != 0 || value.request().planVersion() != 1
                || !codec.checksum(value.termsPayload()).equals(value.termsHash()))
            throw new CommonException(INVALID_STATE);
        var changes = new TreeMap<String, RefundCapacityReservation>();
        for (var r : reservations)
            if (changes.put(r.previous().paymentId(), r) != null) throw new CommonException(INVALID_STATE);
        if (changes.size() != value.allocations().size()) throw new CommonException(INVALID_STATE);
        for (var child : value.allocations()) {
            var r = changes.get(child.refund().paymentId());
            if (r == null || child.refund().state() != RefundState.INIT || child.refund().version() != 0
                    || !r.previous().orderId().equals(value.request().orderId())
                    || !r.previous().payeeMerchantId().equals(value.request().payeeMerchantId())
                    || !r.previous().succeededAmount().getCurrencyValue().equals(child.refund().amount().getCurrencyValue())
                    || r.reserved().reservedMinor() - r.previous().reservedMinor() != child.refund().amount().getCent())
                throw new CommonException(INVALID_STATE);
        }
        var requestRow = converter.toRequestDO(tenant, value);
        var requestNow = LocalDateTime.now(clock);
        requestRow.setCreatedAt(requestNow);
        requestRow.setUpdatedAt(requestNow);
        int requestCount = jdbc.update("""
                INSERT INTO refund_request (id, tenant_id, created_at, updated_at, deleted, order_id, payee_merchant_id,
                    merchant_refund_no, reason_code, request_digest, caller_subject_hash, admission_id, amount_minor, currency,
                    allocation_policy, plan_version, state, succeeded_minor, failed_minor, unknown_count, refund_terms, terms_hash, planned_at, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId, :merchantRefundNo,
                    :reasonCode, :requestDigest, :callerSubjectHash, :admissionId, :amountMinor, :currency, :allocationPolicy,
                    :planVersion, :state, :succeededMinor, :failedMinor, :unknownCount, :refundTerms, :termsHash, :plannedAt, :rowVersion)
                ON CONFLICT (tenant_id, payee_merchant_id, merchant_refund_no) DO NOTHING
                """, new BeanPropertySqlParameterSource(requestRow));
        if (requestCount == 0) throw new PaymentWriteConflictException("Merchant refund request already exists");
        if (requestCount != 1) throw new CommonException(CONCURRENT_UPDATE);
        for (var child : value.allocations()) {
            var plan = converter.toPlanDO(tenant, child);
            var now = LocalDateTime.now(clock);
            plan.setCreatedAt(now);
            plan.setUpdatedAt(now);
            int count = jdbc.update("""
                    INSERT INTO refund_allocation_plan (id, tenant_id, created_at, updated_at, deleted, refund_request_id,
                        plan_version, sequence_no, payment_id, refund_id, amount_minor)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :refundRequestId, :planVersion, :sequenceNo,
                        :paymentId, :refundId, :amountMinor)
                    """, new BeanPropertySqlParameterSource(plan));
            var childRow = converter.toChildDO(tenant, child);
            childRow.setCreatedAt(now);
            childRow.setUpdatedAt(now);
            int childCount = jdbc.update("""
                    INSERT INTO refund_order (id, tenant_id, created_at, updated_at, deleted, refund_request_id, payment_id,
                        original_attempt_id, binding_revision_id, channel, account_scope_hash, original_channel_request_no,
                        original_channel_transaction_no, original_succeeded_at, submission_started_at, channel_refund_no,
                        channel_refund_transaction_no, amount_minor, currency, state, first_unknown_at, terminal_evidence_id,
                        next_query_at, query_sequence, succeeded_at, row_version)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :refundRequestId, :paymentId, :originalAttemptId,
                        :bindingRevisionId, :channel, :accountScopeHash, :originalChannelRequestNo, :originalChannelTransactionNo,
                        :originalSucceededAt, :submissionStartedAt, :channelRefundNo, :channelRefundTransactionNo, :amountMinor,
                        :currency, :state, :firstUnknownAt, :terminalEvidenceId, :nextQueryAt, :querySequence, :succeededAt, :rowVersion)
                    """, new BeanPropertySqlParameterSource(childRow));
            if (count != 1 || childCount != 1)
                throw new CommonException(CONCURRENT_UPDATE);
        }
        for (var r : changes.values()) {
            var before = r.previous();
            var next = r.reserved();
            var args = new MapSqlParameterSource("tenant", tenant).addValue("payment", before.paymentId()).addValue("order", before.orderId())
                    .addValue("payee", before.payeeMerchantId()).addValue("expectedVersion", before.version())
                    .addValue("expectedReserved", before.reservedMinor()).addValue("expectedRefunded", before.refundedMinor())
                    .addValue("succeeded", before.succeededAmount().getCent()).addValue("succeededAt", before.succeededAt())
                    .addValue("reserved", next.reservedMinor()).addValue("version", next.version()).addValue("updated", LocalDateTime.now(clock));
            int changed = jdbc.update("""
                    UPDATE payment_refund_capacity SET reserved_minor = :reserved, row_version = :version, updated_at = :updated
                    WHERE tenant_id = :tenant AND payment_id = :payment AND order_id = :order AND payee_merchant_id = :payee
                        AND row_version = :expectedVersion AND reserved_minor = :expectedReserved AND refunded_minor = :expectedRefunded
                        AND succeeded_minor = :succeeded AND succeeded_at = :succeededAt AND deleted = FALSE
                    """, args);
            if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private RefundRequestDO request(String predicate, MapSqlParameterSource args, boolean lock) {
        return jdbc.query("SELECT " + REQUEST_COLUMNS + " FROM refund_request WHERE tenant_id = :tenant AND deleted = FALSE AND "
                        + predicate + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(RefundRequestDO.class))
                .stream().findFirst().orElse(null);
    }

    private void requireTransaction(String tenant) {
        requireReadTransaction(tenant);
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new CommonException(INVALID_STATE);
    }

    private void requireReadTransaction(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void requireJdbc() {
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }

    private <T> T read(Supplier<T> action) {
        try {
            return action.get();
        } catch (CommonException | org.springframework.dao.DataAccessException failure) {
            throw failure;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
