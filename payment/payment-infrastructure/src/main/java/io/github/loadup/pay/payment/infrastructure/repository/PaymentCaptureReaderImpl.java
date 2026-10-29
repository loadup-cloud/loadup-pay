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

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.port.PaymentCaptureReader;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureBlocker;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureRead;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureScope;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerLine;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentCaptureSourceConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelBindingRevisionDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentAttemptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentChannelReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAcceptanceDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundChannelReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundTransactionClaimDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Bounded, local RR evidence read. Never selects encrypted inputs/contracts, locks financial rows or calls a channel.
 */
@Repository
public class PaymentCaptureReaderImpl implements PaymentCaptureReader {
    private static final String CLAIM_COLUMNS = "id, tenant_id, created_at, updated_at, payment_id, attempt_id, channel, "
            + "binding_revision_id, account_scope_hash, channel_request_no, amount_minor, currency, channel_transaction_no, succeeded_at, receipt_id";
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentCaptureSourceConverter converter;
    private final Clock clock;
    private final int maxRecords;

    public PaymentCaptureReaderImpl(NamedParameterJdbcTemplate jdbc,
                                    PaymentCaptureSourceConverter converter, @Qualifier("paymentClock") Clock clock,
                                    @Value("${loadup.modules.payment.reconciliation.max-records:10000}") int maxRecords) {
        if (maxRecords < 1 || maxRecords > 1000000) throw new CommonException(INVALID_ARGUMENT);
        this.jdbc = jdbc;
        this.converter = converter;
        this.clock = clock;
        this.maxRecords = maxRecords;
    }

    @Override
    public PaymentCaptureRead read(String tenant, PaymentCaptureScope scope) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ).equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())
                || scope == null || !scope.systemZone().equals(clock.getZone().getId()))
            throw new CommonException(INVALID_STATE);
        var started = now();
        var scopeArgs = new MapSqlParameterSource("tenant", tenant).addValue("channel", scope.channel().name()).addValue("scope", scope.accountScopeHash());
        if (jdbc.query("""
                SELECT id FROM channel_binding_revision WHERE tenant_id = :tenant AND deleted = FALSE
                    AND channel = :channel AND account_scope_hash = :scope LIMIT 1
                """, scopeArgs, (rs, index) -> rs.getString("id")).isEmpty())
            return blocked(scope, started, PaymentCaptureBlocker.ACCOUNT_SCOPE_UNKNOWN, scope.accountScopeHash());
        var unresolved = unresolvedCollection(tenant, scope);
        if (unresolved != null)
            return blocked(scope, started, PaymentCaptureBlocker.UNRESOLVED_COLLECTION, unresolved.getId());
        var missing = missingRefundDate(tenant, scope);
        if (missing != null)
            return blocked(scope, started, PaymentCaptureBlocker.REFUND_ACCEPTANCE_MISSING, missing.getId());
        var conflict = refundDateConflict(tenant, scope);
        if (conflict != null)
            return blocked(scope, started, PaymentCaptureBlocker.REFUND_ACCEPTANCE_CONFLICT, conflict.getId());
        var collectionArgs = new MapSqlParameterSource("tenant", tenant).addValue("channel", scope.channel().name())
                .addValue("scope", scope.accountScopeHash()).addValue("start", scope.windowStart()).addValue("end", scope.windowEnd())
                .addValue("limit", maxRecords + 1);
        var collectionRows = jdbc.query("SELECT " + CLAIM_COLUMNS + " FROM channel_transaction_claim "
                        + "WHERE tenant_id = :tenant AND deleted = FALSE AND channel = :channel AND account_scope_hash = :scope "
                        + "AND succeeded_at >= :start AND succeeded_at < :end ORDER BY succeeded_at ASC, id ASC LIMIT :limit",
                collectionArgs, BeanPropertyRowMapper.newInstance(ChannelTransactionClaimDO.class));
        if (collectionRows.size() > maxRecords)
            return blocked(scope, started, PaymentCaptureBlocker.RECORD_LIMIT, collectionRows.getLast().getPaymentId());
        var refundArgs = new MapSqlParameterSource("tenant", tenant).addValue("channel", scope.channel().name())
                .addValue("scope", scope.accountScopeHash()).addValue("start", scope.windowStart()).addValue("end", scope.windowEnd())
                .addValue("limit", maxRecords - collectionRows.size() + 1);
        var refundRows = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, payment_id, attempt_id, channel, binding_revision_id,
                       account_scope_hash, channel_request_no, original_channel_transaction_no, channel_refund_no,
                       amount_minor, currency, accepted_at, receipt_id FROM refund_acceptance_evidence
                WHERE tenant_id = :tenant AND deleted = FALSE AND channel = :channel AND account_scope_hash = :scope
                    AND accepted_at >= :start AND accepted_at < :end ORDER BY accepted_at ASC, id ASC LIMIT :limit
                """, refundArgs, BeanPropertyRowMapper.newInstance(RefundAcceptanceDO.class));
        if (collectionRows.size() + refundRows.size() > maxRecords)
            return blocked(scope, started, PaymentCaptureBlocker.RECORD_LIMIT, refundRows.getLast().getId());
        var result = new ArrayList<PaymentLedgerLine>();
        for (int start = 0; start < collectionRows.size(); start += 256)
            addCollections(tenant, collectionRows.subList(start, Math.min(start + 256, collectionRows.size())), result);
        for (int start = 0; start < refundRows.size(); start += 256)
            addRefunds(tenant, refundRows.subList(start, Math.min(start + 256, refundRows.size())), result);
        long paymentTotal = 0, refundTotal = 0;
        for (var line : result) {
            try {
                if (line.kind() == PaymentLedgerKind.PAYMENT)
                    paymentTotal = Math.addExact(paymentTotal, line.amount().getCent());
                else refundTotal = Math.addExact(refundTotal, line.amount().getCent());
            } catch (ArithmeticException overflow) {
                return blocked(scope, started, PaymentCaptureBlocker.TOTAL_OVERFLOW, line.sourceId());
            }
        }
        try {
            return new PaymentCaptureRead(scope, started, now(), result, null, null);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private PaymentAttemptDO unresolvedCollection(String tenant, PaymentCaptureScope scope) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("channel", scope.channel().name())
                .addValue("scope", scope.accountScopeHash()).addValue("end", scope.windowEnd());
        return jdbc.query("""
                SELECT p.id FROM payment_attempt p LEFT JOIN channel_transaction_claim c
                    ON c.attempt_id = p.id AND c.tenant_id = :tenant AND c.deleted = FALSE
                WHERE p.tenant_id = :tenant AND p.deleted = FALSE AND p.channel = :channel
                    AND p.account_scope_hash = :scope AND p.submission_started_at IS NOT NULL
                    AND p.submission_started_at < :end AND p.no_collection_evidence_id IS NULL AND c.id IS NULL
                ORDER BY p.submission_started_at ASC, p.id ASC LIMIT 1
                """, args, BeanPropertyRowMapper.newInstance(PaymentAttemptDO.class)).stream().findFirst().orElse(null);
    }

    private RefundOrderDO missingRefundDate(String tenant, PaymentCaptureScope scope) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("channel", scope.channel().name())
                .addValue("scope", scope.accountScopeHash()).addValue("end", scope.windowEnd());
        return jdbc.query("""
                SELECT r.id FROM refund_order r LEFT JOIN refund_acceptance_evidence a
                    ON a.id = r.id AND a.tenant_id = :tenant AND a.deleted = FALSE
                WHERE r.tenant_id = :tenant AND r.deleted = FALSE AND r.channel = :channel AND r.account_scope_hash = :scope
                    AND r.submission_started_at IS NOT NULL AND r.submission_started_at < :end AND a.id IS NULL
                ORDER BY r.submission_started_at ASC, r.id ASC LIMIT 1
                """, args, BeanPropertyRowMapper.newInstance(RefundOrderDO.class)).stream().findFirst().orElse(null);
    }

    private RefundOrderDO refundDateConflict(String tenant, PaymentCaptureScope scope) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("channel", scope.channel().name())
                .addValue("scope", scope.accountScopeHash()).addValue("end", scope.windowEnd());
        return jdbc.query("""
                SELECT r.id FROM refund_order r JOIN refund_result_anomaly a
                    ON a.refund_id = r.id AND a.tenant_id = :tenant AND a.deleted = FALSE
                WHERE r.tenant_id = :tenant AND r.deleted = FALSE AND r.channel = :channel AND r.account_scope_hash = :scope
                    AND r.submission_started_at IS NOT NULL AND r.submission_started_at < :end
                    AND a.reason_code = 'REFUND_ACCEPTANCE_TIME_CONFLICT' LIMIT 1
                """, args, BeanPropertyRowMapper.newInstance(RefundOrderDO.class)).stream().findFirst().orElse(null);
    }

    private void addCollections(String tenant, List<ChannelTransactionClaimDO> group, List<PaymentLedgerLine> output) {
        if (group.isEmpty()) return;
        var roots = paymentMap(tenant, group.stream().map(ChannelTransactionClaimDO::getPaymentId).toList());
        var args = new MapSqlParameterSource("tenant", tenant).addValue("ids", group.stream().map(ChannelTransactionClaimDO::getAttemptId).toList());
        var attemptRows = index(jdbc.query("""
                SELECT id, payment_id, state, binding_revision_id, channel, account_scope_hash, channel_request_no,
                       amount_minor, currency FROM payment_attempt WHERE tenant_id = :tenant AND deleted = FALSE AND id IN (:ids)
                """, args, BeanPropertyRowMapper.newInstance(PaymentAttemptDO.class)));
        var bindingArgs = new MapSqlParameterSource("tenant", tenant)
                .addValue("ids", group.stream().map(ChannelTransactionClaimDO::getBindingRevisionId).toList());
        var bindings = index(jdbc.query("""
                SELECT id, channel, account_scope_hash, payee_merchant_id FROM channel_binding_revision
                WHERE tenant_id = :tenant AND deleted = FALSE AND id IN (:ids)
                """, bindingArgs, BeanPropertyRowMapper.newInstance(ChannelBindingRevisionDO.class)));
        var receiptArgs = new MapSqlParameterSource("tenant", tenant).addValue("ids", group.stream().map(ChannelTransactionClaimDO::getReceiptId).toList());
        var proof = index(jdbc.query("""
                SELECT id, payment_id, attempt_id, channel, account_scope_hash, binding_revision_id, channel_request_no, disposition
                FROM payment_channel_receipt WHERE tenant_id = :tenant AND deleted = FALSE AND id IN (:ids)
                """, receiptArgs, BeanPropertyRowMapper.newInstance(PaymentChannelReceiptDO.class)));
        for (var claim : group) {
            var root = required(roots, claim.getPaymentId());
            var attempt = required(attemptRows, claim.getAttemptId());
            var binding = required(bindings, claim.getBindingRevisionId());
            var receipt = required(proof, claim.getReceiptId());
            if (!"SUCCEEDED".equals(root.getState()) || !"SUCCEEDED".equals(attempt.getState()) || root.getRowVersion() == null
                    || !equal(root.getSuccessfulAttemptId(), claim.getAttemptId()) || !equal(root.getTerminalEvidenceId(), claim.getReceiptId())
                    || !equal(root.getSucceededAt(), claim.getSucceededAt()) || !equal(root.getAmountMinor(), claim.getAmountMinor()) || !equal(root.getCurrency(), claim.getCurrency())
                    || !equal(attempt.getPaymentId(), claim.getPaymentId()) || !equal(attempt.getBindingRevisionId(), claim.getBindingRevisionId())
                    || !equal(attempt.getChannel(), claim.getChannel()) || !equal(attempt.getAccountScopeHash(), claim.getAccountScopeHash())
                    || !equal(attempt.getChannelRequestNo(), claim.getChannelRequestNo()) || !equal(attempt.getAmountMinor(), claim.getAmountMinor()) || !equal(attempt.getCurrency(), claim.getCurrency())
                    || !equal(binding.getChannel(), claim.getChannel()) || !equal(binding.getAccountScopeHash(), claim.getAccountScopeHash()) || !equal(binding.getPayeeMerchantId(), root.getPayeeMerchantId())
                    || !equal(receipt.getPaymentId(), claim.getPaymentId()) || !equal(receipt.getAttemptId(), claim.getAttemptId()) || !equal(receipt.getChannel(), claim.getChannel())
                    || !equal(receipt.getBindingRevisionId(), claim.getBindingRevisionId()) || !equal(receipt.getAccountScopeHash(), claim.getAccountScopeHash())
                    || !equal(receipt.getChannelRequestNo(), claim.getChannelRequestNo()) || !"APPLIED".equals(receipt.getDisposition()))
                throw new CommonException(SNAPSHOT_INVALID);
            try {
                output.add(converter.collection(output.size() + 1L, claim, root));
            } catch (RuntimeException corrupt) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        }
    }

    private void addRefunds(String tenant, List<RefundAcceptanceDO> group, List<PaymentLedgerLine> output) {
        var childArgs = new MapSqlParameterSource("tenant", tenant).addValue("ids", group.stream().map(RefundAcceptanceDO::getId).toList());
        var children = index(jdbc.query("""
                SELECT id, payment_id, original_attempt_id, channel, binding_revision_id, account_scope_hash,
                       original_channel_request_no, original_channel_transaction_no, original_succeeded_at, channel_refund_no,
                       amount_minor, currency, state, row_version, terminal_evidence_id, channel_refund_transaction_no, succeeded_at
                FROM refund_order WHERE tenant_id = :tenant AND deleted = FALSE AND id IN (:ids)
                """, childArgs, BeanPropertyRowMapper.newInstance(RefundOrderDO.class)));
        var roots = paymentMap(tenant, group.stream().map(RefundAcceptanceDO::getPaymentId).toList());
        var originalArgs = new MapSqlParameterSource("tenant", tenant).addValue("ids", group.stream().map(RefundAcceptanceDO::getPaymentId).toList());
        var originals = unique(jdbc.query("SELECT " + CLAIM_COLUMNS + " FROM channel_transaction_claim WHERE tenant_id = :tenant "
                        + "AND deleted = FALSE AND payment_id IN (:ids)", originalArgs, BeanPropertyRowMapper.newInstance(ChannelTransactionClaimDO.class)),
                ChannelTransactionClaimDO::getPaymentId);
        // Original collection evidence is checked with the same complete binding checks, regardless of its payment date.
        addCollections(tenant, new ArrayList<>(originals.values()), new ArrayList<>());
        var terminalArgs = new MapSqlParameterSource("tenant", tenant).addValue("ids", group.stream().map(RefundAcceptanceDO::getId).toList());
        var terminalClaims = unique(jdbc.query("""
                SELECT id, tenant_id, refund_id, payment_id, attempt_id, channel, binding_revision_id, account_scope_hash,
                       channel_request_no, original_channel_transaction_no, channel_refund_no, amount_minor, currency,
                       channel_refund_transaction_no, succeeded_at, receipt_id FROM refund_transaction_claim
                WHERE tenant_id = :tenant AND deleted = FALSE AND refund_id IN (:ids)
                """, terminalArgs, BeanPropertyRowMapper.newInstance(RefundTransactionClaimDO.class)), RefundTransactionClaimDO::getRefundId);
        var receiptIds = new HashSet<String>();
        group.forEach(row -> receiptIds.add(row.getReceiptId()));
        children.values().forEach(row -> {
            if (row.getTerminalEvidenceId() != null) receiptIds.add(row.getTerminalEvidenceId());
        });
        var receiptArgs = new MapSqlParameterSource("tenant", tenant).addValue("ids", receiptIds);
        var proof = index(jdbc.query("""
                SELECT id, refund_id, disposition FROM refund_channel_receipt
                WHERE tenant_id = :tenant AND deleted = FALSE AND id IN (:ids)
                """, receiptArgs, BeanPropertyRowMapper.newInstance(RefundChannelReceiptDO.class)));
        for (var date : group) {
            var child = required(children, date.getId());
            var root = required(roots, date.getPaymentId());
            var original = required(originals, date.getPaymentId());
            var receipt = required(proof, date.getReceiptId());
            if (!Set.of("PROCESSING", "UNKNOWN", "SUCCEEDED", "FAILED").contains(child.getState()) || child.getRowVersion() == null
                    || !"SUCCEEDED".equals(root.getState()) || !equal(root.getSuccessfulAttemptId(), original.getAttemptId())
                    || !equal(root.getTerminalEvidenceId(), original.getReceiptId()) || !equal(root.getAmountMinor(), original.getAmountMinor()) || !equal(root.getCurrency(), original.getCurrency())
                    || !equal(root.getSucceededAt(), original.getSucceededAt()) || !equal(date.getPaymentId(), child.getPaymentId()) || !equal(date.getAttemptId(), child.getOriginalAttemptId())
                    || !equal(date.getBindingRevisionId(), child.getBindingRevisionId()) || !equal(date.getChannel(), child.getChannel()) || !equal(date.getAccountScopeHash(), child.getAccountScopeHash())
                    || !equal(date.getChannelRequestNo(), child.getOriginalChannelRequestNo()) || !equal(date.getOriginalChannelTransactionNo(), child.getOriginalChannelTransactionNo())
                    || !equal(date.getChannelRefundNo(), child.getChannelRefundNo()) || !equal(date.getAmountMinor(), child.getAmountMinor()) || !equal(date.getCurrency(), child.getCurrency())
                    || !equal(date.getAttemptId(), original.getAttemptId()) || !equal(date.getBindingRevisionId(), original.getBindingRevisionId()) || !equal(date.getChannel(), original.getChannel())
                    || !equal(date.getAccountScopeHash(), original.getAccountScopeHash()) || !equal(date.getChannelRequestNo(), original.getChannelRequestNo())
                    || !equal(date.getOriginalChannelTransactionNo(), original.getChannelTransactionNo()) || !equal(child.getOriginalSucceededAt(), original.getSucceededAt())
                    || date.getAmountMinor() == null || date.getAmountMinor() > original.getAmountMinor()
                    || date.getAcceptedAt().isBefore(original.getSucceededAt()) || !equal(receipt.getRefundId(), child.getId()) || !acceptedReceipt(receipt))
                throw new CommonException(SNAPSHOT_INVALID);
            var claim = terminalClaims.get(child.getId());
            if ("SUCCEEDED".equals(child.getState())) {
                if (claim == null || !equal(claim.getPaymentId(), date.getPaymentId()) || !equal(claim.getAttemptId(), date.getAttemptId())
                        || !equal(claim.getChannel(), date.getChannel()) || !equal(claim.getAccountScopeHash(), date.getAccountScopeHash()) || !equal(claim.getBindingRevisionId(), date.getBindingRevisionId())
                        || !equal(claim.getChannelRequestNo(), date.getChannelRequestNo()) || !equal(claim.getOriginalChannelTransactionNo(), date.getOriginalChannelTransactionNo())
                        || !equal(claim.getChannelRefundNo(), date.getChannelRefundNo()) || !equal(claim.getAmountMinor(), date.getAmountMinor()) || !equal(claim.getCurrency(), date.getCurrency())
                        || !equal(claim.getChannelRefundTransactionNo(), child.getChannelRefundTransactionNo()) || !equal(claim.getSucceededAt(), child.getSucceededAt())
                        || !equal(claim.getReceiptId(), child.getTerminalEvidenceId()) || child.getSucceededAt().isBefore(date.getAcceptedAt()))
                    throw new CommonException(SNAPSHOT_INVALID);
            } else if (claim != null) throw new CommonException(SNAPSHOT_INVALID);
            if ("SUCCEEDED".equals(child.getState()) || "FAILED".equals(child.getState())) {
                var terminal = required(proof, child.getTerminalEvidenceId());
                if (!equal(terminal.getRefundId(), child.getId()) || !"APPLIED".equals(terminal.getDisposition()))
                    throw new CommonException(SNAPSHOT_INVALID);
            }
            try {
                output.add(converter.refund(output.size() + 1L, date, child));
            } catch (RuntimeException corrupt) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        }
    }

    private Map<String, PaymentOrderDO> paymentMap(String tenant, List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        var args = new MapSqlParameterSource("tenant", tenant).addValue("ids", ids);
        return index(jdbc.query("""
                SELECT id, state, row_version, successful_attempt_id, terminal_evidence_id, succeeded_at,
                       amount_minor, currency, payee_merchant_id FROM payment_order
                WHERE tenant_id = :tenant AND deleted = FALSE AND id IN (:ids)
                """, args, BeanPropertyRowMapper.newInstance(PaymentOrderDO.class)));
    }

    private boolean acceptedReceipt(RefundChannelReceiptDO row) {
        return "APPLIED".equals(row.getDisposition()) || "IGNORED".equals(row.getDisposition());
    }

    private <T extends BaseDO> Map<String, T> index(List<T> rows) {
        return unique(rows, BaseDO::getId);
    }

    private <T> Map<String, T> unique(List<T> rows, Function<T, String> key) {
        var result = new HashMap<String, T>();
        for (var row : rows) {
            String id = key.apply(row);
            if (id == null || result.put(id, row) != null) throw new CommonException(SNAPSHOT_INVALID);
        }
        return result;
    }

    private <T> T required(Map<String, T> rows, String id) {
        var row = rows.get(id);
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        return row;
    }

    private boolean equal(Object a, Object b) {
        return a != null && a.equals(b);
    }

    private PaymentCaptureRead blocked(PaymentCaptureScope scope, LocalDateTime started, PaymentCaptureBlocker blocker, String subject) {
        return new PaymentCaptureRead(scope, started, now(), List.of(), blocker, subject);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
