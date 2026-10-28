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
import io.github.loadup.pay.payment.app.model.RecoveryInventoryCell;
import io.github.loadup.pay.payment.app.port.RecoveryInventoryReader;
import io.github.loadup.pay.payment.infrastructure.converter.RecoveryInventoryConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.model.RecoveryInventoryAggregate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static com.mybatisflex.core.query.QueryMethods.count;
import static com.mybatisflex.core.query.QueryMethods.min;
import static com.mybatisflex.core.query.QueryMethods.sum;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;

@Repository
public class RecoveryInventoryReaderImpl implements RecoveryInventoryReader {
    private final PaymentOrderDOMapper payments;
    private final RefundOrderDOMapper refunds;
    private final RecoveryInventoryConverter converter;

    public RecoveryInventoryReaderImpl(PaymentOrderDOMapper payments, RefundOrderDOMapper refunds, RecoveryInventoryConverter converter) {
        this.payments = payments;
        this.refunds = refunds;
        this.converter = converter;
    }

    @Override
    public List<RecoveryInventoryCell> read(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var collection = payments.selectListByQueryAs(QueryWrapper.create().select(count(PAYMENT_ORDER_DO.ID).as("rowCount"),
                        sum(PAYMENT_ORDER_DO.AMOUNT_MINOR).as("amountMinor"), min(PAYMENT_ORDER_DO.FIRST_UNKNOWN_AT).as("oldestAt"))
                .from(PAYMENT_ORDER_DO).where(PAYMENT_ORDER_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ORDER_DO.DELETED.eq(0))
                .and(PAYMENT_ORDER_DO.STATE.eq("UNKNOWN")), RecoveryInventoryAggregate.class);
        var refund = refunds.selectListByQueryAs(QueryWrapper.create().select(count(REFUND_ORDER_DO.ID).as("rowCount"),
                        sum(REFUND_ORDER_DO.AMOUNT_MINOR).as("amountMinor"), min(REFUND_ORDER_DO.FIRST_UNKNOWN_AT).as("oldestAt"))
                .from(REFUND_ORDER_DO).where(REFUND_ORDER_DO.TENANT_ID.eq(tenant)).and(REFUND_ORDER_DO.DELETED.eq(0))
                .and(REFUND_ORDER_DO.STATE.eq("UNKNOWN")), RecoveryInventoryAggregate.class);
        return List.of(cell(RecoveryInventoryCell.Kind.PAYMENT, collection), cell(RecoveryInventoryCell.Kind.REFUND, refund));
    }

    private RecoveryInventoryCell cell(RecoveryInventoryCell.Kind kind, List<RecoveryInventoryAggregate> rows) {
        try {
            if (rows == null || rows.size() != 1 || rows.getFirst().getRowCount() == null)
                throw new CommonException(SNAPSHOT_INVALID);
            return converter.toCell(kind, rows.getFirst());
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
