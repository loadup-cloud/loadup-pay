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
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentAttemptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAcceptanceDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundChannelReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.mapper.ChannelBindingRevisionDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.ChannelTransactionClaimDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentAttemptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentChannelReceiptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundAcceptanceDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundChannelReceiptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundTransactionClaimDOMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.CHANNEL_BINDING_REVISION_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.CHANNEL_TRANSACTION_CLAIM_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ATTEMPT_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_CHANNEL_RECEIPT_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ACCEPTANCE_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_CHANNEL_RECEIPT_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_RESULT_ANOMALY_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_TRANSACTION_CLAIM_DO;

/**
 * Bounded, local RR evidence read. Never selects encrypted inputs/contracts, locks financial rows or calls a channel.
 */
@Repository
public class PaymentCaptureReaderImpl implements PaymentCaptureReader {
    private final ChannelTransactionClaimDOMapper claims;
    private final PaymentOrderDOMapper payments;
    private final PaymentAttemptDOMapper attempts;
    private final ChannelBindingRevisionDOMapper revisions;
    private final PaymentChannelReceiptDOMapper paymentReceipts;
    private final RefundAcceptanceDOMapper accepted;
    private final RefundOrderDOMapper refunds;
    private final RefundChannelReceiptDOMapper refundReceipts;
    private final RefundTransactionClaimDOMapper refundClaims;
    private final PaymentCaptureSourceConverter converter;
    private final Clock clock;
    private final int maxRecords;

    public PaymentCaptureReaderImpl(ChannelTransactionClaimDOMapper claims, PaymentOrderDOMapper payments, PaymentAttemptDOMapper attempts,
                                    ChannelBindingRevisionDOMapper revisions, PaymentChannelReceiptDOMapper paymentReceipts, RefundAcceptanceDOMapper accepted,
                                    RefundOrderDOMapper refunds, RefundChannelReceiptDOMapper refundReceipts, RefundTransactionClaimDOMapper refundClaims,
                                    PaymentCaptureSourceConverter converter, @Qualifier("paymentClock") Clock clock,
                                    @Value("${loadup.modules.payment.reconciliation.max-records:10000}") int maxRecords) {
        if (maxRecords < 1 || maxRecords > 1000000) throw new CommonException(INVALID_ARGUMENT);
        this.claims = claims;
        this.payments = payments;
        this.attempts = attempts;
        this.revisions = revisions;
        this.paymentReceipts = paymentReceipts;
        this.accepted = accepted;
        this.refunds = refunds;
        this.refundReceipts = refundReceipts;
        this.refundClaims = refundClaims;
        this.converter = converter;
        this.clock = clock;
        this.maxRecords = maxRecords;
    }

    @Override
    public PaymentCaptureRead read(String tenant, PaymentCaptureScope scope) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ).equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                || scope == null || !scope.systemZone().equals(clock.getZone().getId()))
            throw new CommonException(INVALID_STATE);
        var started = now();
        if (revisions.selectCountByQuery(QueryWrapper.create().where(CHANNEL_BINDING_REVISION_DO.TENANT_ID.eq(tenant)).and(CHANNEL_BINDING_REVISION_DO.DELETED.eq(0))
                .and(CHANNEL_BINDING_REVISION_DO.CHANNEL.eq(scope.channel().name())).and(CHANNEL_BINDING_REVISION_DO.ACCOUNT_SCOPE_HASH.eq(scope.accountScopeHash()))) == 0)
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
        var collectionRows = claims.selectListByQuery(QueryWrapper.create().where(CHANNEL_TRANSACTION_CLAIM_DO.TENANT_ID.eq(tenant))
                .and(CHANNEL_TRANSACTION_CLAIM_DO.DELETED.eq(0)).and(CHANNEL_TRANSACTION_CLAIM_DO.CHANNEL.eq(scope.channel().name()))
                .and(CHANNEL_TRANSACTION_CLAIM_DO.ACCOUNT_SCOPE_HASH.eq(scope.accountScopeHash())).and(CHANNEL_TRANSACTION_CLAIM_DO.SUCCEEDED_AT.ge(scope.windowStart()))
                .and(CHANNEL_TRANSACTION_CLAIM_DO.SUCCEEDED_AT.lt(scope.windowEnd())).orderBy(CHANNEL_TRANSACTION_CLAIM_DO.SUCCEEDED_AT.asc(), CHANNEL_TRANSACTION_CLAIM_DO.ID.asc()).limit(maxRecords + 1));
        if (collectionRows.size() > maxRecords)
            return blocked(scope, started, PaymentCaptureBlocker.RECORD_LIMIT, collectionRows.getLast().getPaymentId());
        var refundRows = accepted.selectListByQuery(QueryWrapper.create().where(REFUND_ACCEPTANCE_DO.TENANT_ID.eq(tenant)).and(REFUND_ACCEPTANCE_DO.DELETED.eq(0))
                .and(REFUND_ACCEPTANCE_DO.CHANNEL.eq(scope.channel().name())).and(REFUND_ACCEPTANCE_DO.ACCOUNT_SCOPE_HASH.eq(scope.accountScopeHash()))
                .and(REFUND_ACCEPTANCE_DO.ACCEPTED_AT.ge(scope.windowStart())).and(REFUND_ACCEPTANCE_DO.ACCEPTED_AT.lt(scope.windowEnd()))
                .orderBy(REFUND_ACCEPTANCE_DO.ACCEPTED_AT.asc(), REFUND_ACCEPTANCE_DO.ID.asc()).limit(maxRecords - collectionRows.size() + 1));
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
        return attempts.selectOneByQuery(QueryWrapper.create().select(PAYMENT_ATTEMPT_DO.ID).from(PAYMENT_ATTEMPT_DO)
                .leftJoin(CHANNEL_TRANSACTION_CLAIM_DO).on(CHANNEL_TRANSACTION_CLAIM_DO.ATTEMPT_ID.eq(PAYMENT_ATTEMPT_DO.ID)
                        .and(CHANNEL_TRANSACTION_CLAIM_DO.TENANT_ID.eq(tenant)).and(CHANNEL_TRANSACTION_CLAIM_DO.DELETED.eq(0)))
                .where(PAYMENT_ATTEMPT_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ATTEMPT_DO.DELETED.eq(0)).and(PAYMENT_ATTEMPT_DO.CHANNEL.eq(scope.channel().name()))
                .and(PAYMENT_ATTEMPT_DO.ACCOUNT_SCOPE_HASH.eq(scope.accountScopeHash())).and(PAYMENT_ATTEMPT_DO.SUBMISSION_STARTED_AT.isNotNull())
                .and(PAYMENT_ATTEMPT_DO.SUBMISSION_STARTED_AT.lt(scope.windowEnd())).and(PAYMENT_ATTEMPT_DO.NO_COLLECTION_EVIDENCE_ID.isNull())
                .and(CHANNEL_TRANSACTION_CLAIM_DO.ID.isNull()).orderBy(PAYMENT_ATTEMPT_DO.SUBMISSION_STARTED_AT.asc(), PAYMENT_ATTEMPT_DO.ID.asc()).limit(1));
    }

    private RefundOrderDO missingRefundDate(String tenant, PaymentCaptureScope scope) {
        return refunds.selectOneByQuery(refundWindow(tenant, scope).select(REFUND_ORDER_DO.ID).leftJoin(REFUND_ACCEPTANCE_DO)
                .on(REFUND_ACCEPTANCE_DO.ID.eq(REFUND_ORDER_DO.ID).and(REFUND_ACCEPTANCE_DO.TENANT_ID.eq(tenant)).and(REFUND_ACCEPTANCE_DO.DELETED.eq(0)))
                .and(REFUND_ACCEPTANCE_DO.ID.isNull()).orderBy(REFUND_ORDER_DO.SUBMISSION_STARTED_AT.asc(), REFUND_ORDER_DO.ID.asc()).limit(1));
    }

    private RefundOrderDO refundDateConflict(String tenant, PaymentCaptureScope scope) {
        return refunds.selectOneByQuery(refundWindow(tenant, scope).select(REFUND_ORDER_DO.ID).innerJoin(REFUND_RESULT_ANOMALY_DO)
                .on(REFUND_RESULT_ANOMALY_DO.REFUND_ID.eq(REFUND_ORDER_DO.ID).and(REFUND_RESULT_ANOMALY_DO.TENANT_ID.eq(tenant)).and(REFUND_RESULT_ANOMALY_DO.DELETED.eq(0)))
                .and(REFUND_RESULT_ANOMALY_DO.REASON_CODE.eq("REFUND_ACCEPTANCE_TIME_CONFLICT")).limit(1));
    }

    private QueryWrapper refundWindow(String tenant, PaymentCaptureScope scope) {
        return QueryWrapper.create().from(REFUND_ORDER_DO).where(REFUND_ORDER_DO.TENANT_ID.eq(tenant)).and(REFUND_ORDER_DO.DELETED.eq(0))
                .and(REFUND_ORDER_DO.CHANNEL.eq(scope.channel().name())).and(REFUND_ORDER_DO.ACCOUNT_SCOPE_HASH.eq(scope.accountScopeHash()))
                .and(REFUND_ORDER_DO.SUBMISSION_STARTED_AT.isNotNull()).and(REFUND_ORDER_DO.SUBMISSION_STARTED_AT.lt(scope.windowEnd()));
    }

    private void addCollections(String tenant, List<ChannelTransactionClaimDO> group, List<PaymentLedgerLine> output) {
        var roots = paymentMap(tenant, group.stream().map(ChannelTransactionClaimDO::getPaymentId).toList());
        var attemptRows = index(attempts.selectListByQuery(QueryWrapper.create().select(PAYMENT_ATTEMPT_DO.ID, PAYMENT_ATTEMPT_DO.PAYMENT_ID, PAYMENT_ATTEMPT_DO.STATE,
                        PAYMENT_ATTEMPT_DO.BINDING_REVISION_ID, PAYMENT_ATTEMPT_DO.CHANNEL, PAYMENT_ATTEMPT_DO.ACCOUNT_SCOPE_HASH, PAYMENT_ATTEMPT_DO.CHANNEL_REQUEST_NO,
                        PAYMENT_ATTEMPT_DO.AMOUNT_MINOR, PAYMENT_ATTEMPT_DO.CURRENCY).where(PAYMENT_ATTEMPT_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ATTEMPT_DO.DELETED.eq(0))
                .and(PAYMENT_ATTEMPT_DO.ID.in(group.stream().map(ChannelTransactionClaimDO::getAttemptId).toList()))));
        var bindings = index(revisions.selectListByQuery(QueryWrapper.create().select(CHANNEL_BINDING_REVISION_DO.ID, CHANNEL_BINDING_REVISION_DO.CHANNEL,
                        CHANNEL_BINDING_REVISION_DO.ACCOUNT_SCOPE_HASH, CHANNEL_BINDING_REVISION_DO.PAYEE_MERCHANT_ID).where(CHANNEL_BINDING_REVISION_DO.TENANT_ID.eq(tenant))
                .and(CHANNEL_BINDING_REVISION_DO.DELETED.eq(0)).and(CHANNEL_BINDING_REVISION_DO.ID.in(group.stream().map(ChannelTransactionClaimDO::getBindingRevisionId).toList()))));
        var proof = index(paymentReceipts.selectListByQuery(QueryWrapper.create().select(PAYMENT_CHANNEL_RECEIPT_DO.ID, PAYMENT_CHANNEL_RECEIPT_DO.PAYMENT_ID,
                        PAYMENT_CHANNEL_RECEIPT_DO.ATTEMPT_ID, PAYMENT_CHANNEL_RECEIPT_DO.CHANNEL, PAYMENT_CHANNEL_RECEIPT_DO.ACCOUNT_SCOPE_HASH,
                        PAYMENT_CHANNEL_RECEIPT_DO.BINDING_REVISION_ID, PAYMENT_CHANNEL_RECEIPT_DO.CHANNEL_REQUEST_NO, PAYMENT_CHANNEL_RECEIPT_DO.DISPOSITION)
                .where(PAYMENT_CHANNEL_RECEIPT_DO.TENANT_ID.eq(tenant)).and(PAYMENT_CHANNEL_RECEIPT_DO.DELETED.eq(0))
                .and(PAYMENT_CHANNEL_RECEIPT_DO.ID.in(group.stream().map(ChannelTransactionClaimDO::getReceiptId).toList()))));
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
        var children = index(refunds.selectListByQuery(QueryWrapper.create().select(REFUND_ORDER_DO.ID, REFUND_ORDER_DO.PAYMENT_ID, REFUND_ORDER_DO.ORIGINAL_ATTEMPT_ID,
                        REFUND_ORDER_DO.CHANNEL, REFUND_ORDER_DO.BINDING_REVISION_ID, REFUND_ORDER_DO.ACCOUNT_SCOPE_HASH, REFUND_ORDER_DO.ORIGINAL_CHANNEL_REQUEST_NO,
                        REFUND_ORDER_DO.ORIGINAL_CHANNEL_TRANSACTION_NO, REFUND_ORDER_DO.ORIGINAL_SUCCEEDED_AT, REFUND_ORDER_DO.CHANNEL_REFUND_NO,
                        REFUND_ORDER_DO.AMOUNT_MINOR, REFUND_ORDER_DO.CURRENCY, REFUND_ORDER_DO.STATE, REFUND_ORDER_DO.ROW_VERSION, REFUND_ORDER_DO.TERMINAL_EVIDENCE_ID,
                        REFUND_ORDER_DO.CHANNEL_REFUND_TRANSACTION_NO, REFUND_ORDER_DO.SUCCEEDED_AT).where(REFUND_ORDER_DO.TENANT_ID.eq(tenant)).and(REFUND_ORDER_DO.DELETED.eq(0))
                .and(REFUND_ORDER_DO.ID.in(group.stream().map(RefundAcceptanceDO::getId).toList()))));
        var roots = paymentMap(tenant, group.stream().map(RefundAcceptanceDO::getPaymentId).toList());
        var originals = unique(claims.selectListByQuery(QueryWrapper.create().where(CHANNEL_TRANSACTION_CLAIM_DO.TENANT_ID.eq(tenant)).and(CHANNEL_TRANSACTION_CLAIM_DO.DELETED.eq(0))
                .and(CHANNEL_TRANSACTION_CLAIM_DO.PAYMENT_ID.in(group.stream().map(RefundAcceptanceDO::getPaymentId).toList()))), ChannelTransactionClaimDO::getPaymentId);
        // Original collection evidence is checked with the same complete binding checks, regardless of its payment date.
        addCollections(tenant, new ArrayList<>(originals.values()), new ArrayList<>());
        var terminalClaims = unique(refundClaims.selectListByQuery(QueryWrapper.create().where(REFUND_TRANSACTION_CLAIM_DO.TENANT_ID.eq(tenant)).and(REFUND_TRANSACTION_CLAIM_DO.DELETED.eq(0))
                .and(REFUND_TRANSACTION_CLAIM_DO.REFUND_ID.in(group.stream().map(RefundAcceptanceDO::getId).toList()))), RefundTransactionClaimDO::getRefundId);
        var receiptIds = new HashSet<String>();
        group.forEach(row -> receiptIds.add(row.getReceiptId()));
        children.values().forEach(row -> {
            if (row.getTerminalEvidenceId() != null) receiptIds.add(row.getTerminalEvidenceId());
        });
        var proof = index(refundReceipts.selectListByQuery(QueryWrapper.create().select(REFUND_CHANNEL_RECEIPT_DO.ID, REFUND_CHANNEL_RECEIPT_DO.REFUND_ID, REFUND_CHANNEL_RECEIPT_DO.DISPOSITION)
                .where(REFUND_CHANNEL_RECEIPT_DO.TENANT_ID.eq(tenant)).and(REFUND_CHANNEL_RECEIPT_DO.DELETED.eq(0)).and(REFUND_CHANNEL_RECEIPT_DO.ID.in(receiptIds))));
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
        return index(payments.selectListByQuery(QueryWrapper.create().select(PAYMENT_ORDER_DO.ID, PAYMENT_ORDER_DO.STATE, PAYMENT_ORDER_DO.ROW_VERSION,
                PAYMENT_ORDER_DO.SUCCESSFUL_ATTEMPT_ID, PAYMENT_ORDER_DO.TERMINAL_EVIDENCE_ID, PAYMENT_ORDER_DO.SUCCEEDED_AT, PAYMENT_ORDER_DO.AMOUNT_MINOR,
                PAYMENT_ORDER_DO.CURRENCY, PAYMENT_ORDER_DO.PAYEE_MERCHANT_ID).where(PAYMENT_ORDER_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ORDER_DO.DELETED.eq(0)).and(PAYMENT_ORDER_DO.ID.in(ids))));
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
