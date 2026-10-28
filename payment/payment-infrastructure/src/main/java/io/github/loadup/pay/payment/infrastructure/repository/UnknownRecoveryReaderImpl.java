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
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.model.UnknownRecoveryEntry;
import io.github.loadup.pay.payment.app.model.UnknownRecoveryWindow;
import io.github.loadup.pay.payment.app.port.UnknownRecoveryReader;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;
import io.github.loadup.pay.payment.infrastructure.converter.UnknownRecoveryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.model.UnknownRecoveryProjection;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_RECOVERY_CURSOR_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_RECOVERY_CURSOR_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_REQUEST_DO;

@Repository
public class UnknownRecoveryReaderImpl implements UnknownRecoveryReader {
    private final PaymentOrderDOMapper payments;
    private final RefundOrderDOMapper refunds;
    private final UnknownRecoveryStorageConverter converter;

    public UnknownRecoveryReaderImpl(PaymentOrderDOMapper payments, RefundOrderDOMapper refunds, UnknownRecoveryStorageConverter converter) {
        this.payments = payments;
        this.refunds = refunds;
        this.converter = converter;
    }

    @Override
    public List<UnknownRecoveryEntry> list(String tenant, PaymentRecoveryKind kind, UnknownRecoveryWindow window) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (kind == null || window == null) throw new CommonException(INVALID_ARGUMENT);
        var rows = switch (kind) {
            case COLLECTION ->
                    payments.selectListByQueryAs(collection(tenant, window), UnknownRecoveryProjection.class);
            case REFUND_REQUEST -> refunds.selectListByQueryAs(refund(tenant, window), UnknownRecoveryProjection.class);
        };
        try {
            if (rows == null) throw new CommonException(SNAPSHOT_INVALID);
            return rows.stream().map(row -> converter.toEntry(kind, row)).toList();
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private QueryWrapper collection(String tenant, UnknownRecoveryWindow window) {
        var query = QueryWrapper.create().select(PAYMENT_ORDER_DO.ID.as("recordId"), PAYMENT_ORDER_DO.ID.as("subjectId"),
                        PAYMENT_ORDER_DO.ORDER_ID.as("orderId"), PAYMENT_ORDER_DO.PAYEE_MERCHANT_ID.as("payeeMerchantId"),
                        PAYMENT_ORDER_DO.AMOUNT_MINOR.as("amountMinor"), PAYMENT_ORDER_DO.CURRENCY.as("currency"),
                        PAYMENT_ORDER_DO.FIRST_UNKNOWN_AT.as("firstUnknownAt"), PAYMENT_ORDER_DO.ROW_VERSION.as("rowVersion"),
                        PAYMENT_RECOVERY_CURSOR_DO.GENERATION.as("recoveryGeneration"), PAYMENT_RECOVERY_CURSOR_DO.COMPLETED.as("recoveryCompleted"))
                .from(PAYMENT_ORDER_DO)
                .leftJoin(PAYMENT_RECOVERY_CURSOR_DO).on(PAYMENT_RECOVERY_CURSOR_DO.ID.eq(PAYMENT_ORDER_DO.ID)
                        .and(PAYMENT_RECOVERY_CURSOR_DO.TENANT_ID.eq(tenant)).and(PAYMENT_RECOVERY_CURSOR_DO.DELETED.eq(0)))
                .where(PAYMENT_ORDER_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ORDER_DO.DELETED.eq(0))
                .and(PAYMENT_ORDER_DO.STATE.eq("UNKNOWN"));
        if (window.cutoff() != null) query.and(PAYMENT_ORDER_DO.FIRST_UNKNOWN_AT.le(window.cutoff()));
        if (window.afterFirstUnknownAt() != null)
            query.and(PAYMENT_ORDER_DO.FIRST_UNKNOWN_AT.gt(window.afterFirstUnknownAt())
                    .or(PAYMENT_ORDER_DO.FIRST_UNKNOWN_AT.eq(window.afterFirstUnknownAt()).and(PAYMENT_ORDER_DO.ID.gt(window.afterRecordId()))));
        return query.orderBy(PAYMENT_ORDER_DO.FIRST_UNKNOWN_AT.asc(), PAYMENT_ORDER_DO.ID.asc()).limit(window.limit() + 1);
    }

    private QueryWrapper refund(String tenant, UnknownRecoveryWindow window) {
        var query = QueryWrapper.create().select(REFUND_ORDER_DO.ID.as("recordId"), REFUND_ORDER_DO.REFUND_REQUEST_ID.as("subjectId"),
                        REFUND_REQUEST_DO.ORDER_ID.as("orderId"), REFUND_REQUEST_DO.PAYEE_MERCHANT_ID.as("payeeMerchantId"),
                        REFUND_ORDER_DO.AMOUNT_MINOR.as("amountMinor"), REFUND_ORDER_DO.CURRENCY.as("currency"),
                        REFUND_ORDER_DO.FIRST_UNKNOWN_AT.as("firstUnknownAt"), REFUND_ORDER_DO.ROW_VERSION.as("rowVersion"),
                        REFUND_RECOVERY_CURSOR_DO.GENERATION.as("recoveryGeneration"), REFUND_RECOVERY_CURSOR_DO.COMPLETED.as("recoveryCompleted"))
                .from(REFUND_ORDER_DO)
                .leftJoin(REFUND_REQUEST_DO).on(REFUND_REQUEST_DO.ID.eq(REFUND_ORDER_DO.REFUND_REQUEST_ID)
                        .and(REFUND_REQUEST_DO.TENANT_ID.eq(tenant)).and(REFUND_REQUEST_DO.DELETED.eq(0)))
                .leftJoin(REFUND_RECOVERY_CURSOR_DO).on(REFUND_RECOVERY_CURSOR_DO.ID.eq(REFUND_ORDER_DO.REFUND_REQUEST_ID)
                        .and(REFUND_RECOVERY_CURSOR_DO.TENANT_ID.eq(tenant)).and(REFUND_RECOVERY_CURSOR_DO.DELETED.eq(0)))
                .where(REFUND_ORDER_DO.TENANT_ID.eq(tenant)).and(REFUND_ORDER_DO.DELETED.eq(0))
                .and(REFUND_ORDER_DO.STATE.eq("UNKNOWN"));
        if (window.cutoff() != null) query.and(REFUND_ORDER_DO.FIRST_UNKNOWN_AT.le(window.cutoff()));
        if (window.afterFirstUnknownAt() != null)
            query.and(REFUND_ORDER_DO.FIRST_UNKNOWN_AT.gt(window.afterFirstUnknownAt())
                    .or(REFUND_ORDER_DO.FIRST_UNKNOWN_AT.eq(window.afterFirstUnknownAt()).and(REFUND_ORDER_DO.ID.gt(window.afterRecordId()))));
        return query.orderBy(REFUND_ORDER_DO.FIRST_UNKNOWN_AT.asc(), REFUND_ORDER_DO.ID.asc()).limit(window.limit() + 1);
    }
}
