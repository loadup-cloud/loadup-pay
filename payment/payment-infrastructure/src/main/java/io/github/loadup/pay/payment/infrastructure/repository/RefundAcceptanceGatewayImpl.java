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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.domain.gateway.RefundAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.RefundAcceptanceEvidence;
import io.github.loadup.pay.payment.infrastructure.converter.RefundAcceptanceStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.RefundStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAcceptanceDO;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundAcceptanceDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundChannelReceiptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ACCEPTANCE_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_CHANNEL_RECEIPT_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;

@Repository
public class RefundAcceptanceGatewayImpl implements RefundAcceptanceGateway {
    private final RefundAcceptanceDOMapper mapper;
    private final RefundOrderDOMapper children;
    private final RefundChannelReceiptDOMapper receipts;
    private final RefundAcceptanceStorageConverter converter;
    private final RefundStorageConverter storage;

    public RefundAcceptanceGatewayImpl(RefundAcceptanceDOMapper mapper, RefundOrderDOMapper children, RefundChannelReceiptDOMapper receipts,
                                       RefundAcceptanceStorageConverter converter, RefundStorageConverter storage) {
        this.mapper = mapper;
        this.children = children;
        this.receipts = receipts;
        this.converter = converter;
        this.storage = storage;
    }

    @Override
    public boolean remember(String tenant, RefundAcceptanceEvidence value, long expectedChildVersion) {
        transaction(tenant, true);
        if (value == null || expectedChildVersion < 0) throw new CommonException(INVALID_ARGUMENT);
        var child = children.selectOneByQuery(QueryWrapper.create().where(REFUND_ORDER_DO.TENANT_ID.eq(tenant)).and(REFUND_ORDER_DO.DELETED.eq(0))
                .and(REFUND_ORDER_DO.ID.eq(value.refundId())).forUpdate());
        if (child == null || child.getRowVersion() == null || child.getRowVersion() != expectedChildVersion
                || child.getSubmissionStartedAt() == null || child.getOriginalSucceededAt() == null
                || value.acceptedAt().isBefore(child.getOriginalSucceededAt()) || !value.original().equals(storage.toIdentity(child))
                || !Objects.equals(value.originalChannelTransactionNo(), child.getOriginalChannelTransactionNo())
                || !Objects.equals(value.channelRefundNo(), child.getChannelRefundNo()) || !Objects.equals(child.getAmountMinor(), value.amount().getCent())
                || !Objects.equals(child.getCurrency(), value.amount().getCurrencyValue()))
            throw new CommonException(SNAPSHOT_INVALID);
        proof(tenant, value.refundId(), value.receiptId());
        var row = mapper.selectOneByQuery(key(tenant, value.refundId()).forUpdate());
        if (row != null) {
            var original = read(row);
            proof(tenant, original.refundId(), original.receiptId());
            if (!sameBinding(original, value)) throw new CommonException(SNAPSHOT_INVALID);
            return original.acceptedAt().equals(value.acceptedAt());
        }
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
        return true;
    }

    @Override
    public Optional<RefundAcceptanceEvidence> find(String tenant, String refundId) {
        transaction(tenant, false);
        var row = mapper.selectOneByQuery(key(tenant, refundId));
        if (row == null) return Optional.empty();
        var value = read(row);
        proof(tenant, value.refundId(), value.receiptId());
        return Optional.of(value);
    }

    private RefundAcceptanceEvidence read(RefundAcceptanceDO row) {
        try {
            return converter.toModel(row);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private boolean sameBinding(RefundAcceptanceEvidence a, RefundAcceptanceEvidence b) {
        return a.refundId().equals(b.refundId()) && a.original().equals(b.original()) && a.originalChannelTransactionNo().equals(b.originalChannelTransactionNo())
                && a.channelRefundNo().equals(b.channelRefundNo()) && a.amount().equals(b.amount());
    }

    private void proof(String tenant, String refundId, String receiptId) {
        var receipt = receipts.selectOneByQuery(QueryWrapper.create().where(REFUND_CHANNEL_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(REFUND_CHANNEL_RECEIPT_DO.DELETED.eq(0)).and(REFUND_CHANNEL_RECEIPT_DO.ID.eq(receiptId))
                .and(REFUND_CHANNEL_RECEIPT_DO.REFUND_ID.eq(refundId)));
        if (receipt == null || !"APPLIED".equals(receipt.getDisposition()) && !"IGNORED".equals(receipt.getDisposition()))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private QueryWrapper key(String tenant, String refundId) {
        DomainChecks.id(refundId);
        return QueryWrapper.create().where(REFUND_ACCEPTANCE_DO.TENANT_ID.eq(tenant)).and(REFUND_ACCEPTANCE_DO.DELETED.eq(0)).and(REFUND_ACCEPTANCE_DO.ID.eq(refundId));
    }

    private void transaction(String tenant, boolean write) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || write && TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
    }
}
