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
import io.github.loadup.pay.payment.app.model.RefundSnapshotFacts;
import io.github.loadup.pay.payment.app.model.RefundSummaryFacts;
import io.github.loadup.pay.payment.app.port.RefundSnapshotReader;
import io.github.loadup.pay.payment.infrastructure.converter.RefundProjectionStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.OrderRefundSummaryDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundRequestDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.ORDER_REFUND_SUMMARY_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_REQUEST_DO;

@Repository
public class RefundSnapshotReaderImpl implements RefundSnapshotReader {
    private final RefundOrderDOMapper children;
    private final RefundRequestDOMapper requests;
    private final OrderRefundSummaryDOMapper summaries;
    private final RefundProjectionStorageConverter converter;

    public RefundSnapshotReaderImpl(RefundOrderDOMapper children, RefundRequestDOMapper requests, OrderRefundSummaryDOMapper summaries,
                                    RefundProjectionStorageConverter converter) {
        this.children = children;
        this.requests = requests;
        this.summaries = summaries;
        this.converter = converter;
    }

    @Override
    public RefundSnapshotFacts child(String tenant, String refundId) {
        writable(tenant);
        var row = children.selectOneByQuery(QueryWrapper.create().where(REFUND_ORDER_DO.TENANT_ID.eq(tenant))
                .and(REFUND_ORDER_DO.DELETED.eq(0)).and(REFUND_ORDER_DO.ID.eq(refundId)).forUpdate());
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        var header = requests.selectOneByQuery(QueryWrapper.create().where(REFUND_REQUEST_DO.TENANT_ID.eq(tenant))
                .and(REFUND_REQUEST_DO.DELETED.eq(0)).and(REFUND_REQUEST_DO.ID.eq(row.getRefundRequestId())));
        if (header == null || !header.getCurrency().equals(row.getCurrency()))
            throw new CommonException(SNAPSHOT_INVALID);
        try {
            return converter.toFacts(row, header);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public RefundSummaryFacts summary(String tenant, String orderId) {
        writable(tenant);
        var row = summaries.selectOneByQuery(QueryWrapper.create().where(ORDER_REFUND_SUMMARY_DO.TENANT_ID.eq(tenant))
                .and(ORDER_REFUND_SUMMARY_DO.DELETED.eq(0)).and(ORDER_REFUND_SUMMARY_DO.ORDER_ID.eq(orderId)).forUpdate());
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        try {
            return converter.toFacts(row, converter.toSummary(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
