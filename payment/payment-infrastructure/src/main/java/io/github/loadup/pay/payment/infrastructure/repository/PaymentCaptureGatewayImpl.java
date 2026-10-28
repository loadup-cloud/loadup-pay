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
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentCaptureConverter;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash;
import io.github.loadup.pay.payment.domain.gateway.PaymentCaptureGateway;
import io.github.loadup.pay.payment.domain.model.PaymentCapture;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureRead;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerLine;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentCaptureStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentCaptureDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentCaptureDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentCaptureLineDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_CAPTURE_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_CAPTURE_LINE_DO;

@Repository
public class PaymentCaptureGatewayImpl implements PaymentCaptureGateway {
    private final PaymentCaptureDOMapper captures;
    private final PaymentCaptureLineDOMapper lines;
    private final PaymentCaptureStorageConverter converter;
    private final PaymentCaptureConverter views;

    public PaymentCaptureGatewayImpl(PaymentCaptureDOMapper captures, PaymentCaptureLineDOMapper lines, PaymentCaptureStorageConverter converter, PaymentCaptureConverter views) {
        this.captures = captures;
        this.lines = lines;
        this.converter = converter;
        this.views = views;
    }

    @Override
    public Optional<PaymentCapture> findRequest(String tenant, String caller, String key) {
        context(tenant, false);
        DomainChecks.id(caller);
        DomainChecks.text(key, 128);
        return model(captures.selectOneByQuery(scope(tenant).and(PAYMENT_CAPTURE_DO.CALLER_SERVICE_ID.eq(caller)).and(PAYMENT_CAPTURE_DO.REQUEST_KEY.eq(key))));
    }

    @Override
    public Optional<PaymentCapture> find(String tenant, String id) {
        context(tenant, false);
        return model(captures.selectOneByQuery(key(tenant, id)));
    }

    @Override
    public Optional<PaymentCapture> lock(String tenant, String id) {
        context(tenant, true);
        return model(captures.selectOneByQuery(key(tenant, id).forUpdate()));
    }

    @Override
    public void insert(String tenant, PaymentCapture value) {
        context(tenant, true);
        if (value.state() != PaymentCaptureState.INIT || value.version() != 0L)
            throw new CommonException(INVALID_STATE);
        if (captures.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void complete(String tenant, PaymentCapture original, PaymentCapture next, PaymentCaptureRead read) {
        context(tenant, true);
        var current = lock(tenant, original.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!current.equals(original) || original.state() != PaymentCaptureState.INIT || !original.complete(read, next.contentHash()).equals(next))
            throw new CommonException(SNAPSHOT_INVALID);
        var query = lineScope(tenant, original.id());
        if (lines.selectCountByQuery(query) != 0) throw new CommonException(SNAPSHOT_INVALID);
        if (next.state() == PaymentCaptureState.SEALED) {
            if (!next.contentHash().equals(PaymentCaptureContentHash.compute(next.id(), next.scope().channel(), next.scope().accountScopeHash(), next.scope().billDate(),
                    next.scope().statementProfile(), next.scope().systemZone(), views.offset(next.startedAt(), next.scope().systemZone()),
                    views.offset(next.finishedAt(), next.scope().systemZone()), read.lines().stream().map(line -> views.toDTO(line, next.scope().systemZone())).toList())))
                throw new CommonException(SNAPSHOT_INVALID);
            for (var line : read.lines())
                if (lines.insert(converter.toLineDO(tenant, original.id(), line)) != 1)
                    throw new CommonException(CONCURRENT_UPDATE);
        }
        if (captures.updateByQuery(converter.toDO(tenant, next), true, key(tenant, original.id()).and(PAYMENT_CAPTURE_DO.ROW_VERSION.eq(0L))
                .and(PAYMENT_CAPTURE_DO.STATE.eq("INIT"))) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public PageResult<PaymentLedgerLine> page(String tenant, PaymentCapture capture, int page, int size) {
        context(tenant, false);
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly() || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()) || page < 1 || size < 1 || size > 100 || capture.state() != PaymentCaptureState.SEALED)
            throw new CommonException(INVALID_STATE);
        long expected = Math.addExact(capture.paymentCount(), capture.refundCount());
        var query = lineScope(tenant, capture.id());
        if (lines.selectCountByQuery(query) != expected) throw new CommonException(SNAPSHOT_INVALID);
        long offset = (long) (page - 1) * size;
        var rows = lines.selectListByQuery(query.orderBy(PAYMENT_CAPTURE_LINE_DO.ORDINAL.asc()).limit(size).offset(offset));
        var result = new java.util.ArrayList<PaymentLedgerLine>();
        try {
            for (var row : rows) {
                if (row.getOrdinal() == null || row.getSourceVersion() == null || row.getAmountMinor() == null)
                    throw new CommonException(SNAPSHOT_INVALID);
                var line = converter.toLine(row);
                if (line.ordinal() != offset + result.size() + 1L || !capture.id().equals(row.getCaptureId())
                        || !PaymentCaptureContentHash.key(line.kind().name(), line.channelTransactionNo(), line.channelRefundNo()).equals(row.getKeyHash())
                        || line.sourceAt().isBefore(capture.scope().windowStart()) || !line.sourceAt().isBefore(capture.scope().windowEnd()))
                    throw new CommonException(SNAPSHOT_INVALID);
                result.add(line);
            }
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return PageResult.of(result, expected, page, size);
    }

    private Optional<PaymentCapture> model(PaymentCaptureDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getRowVersion() == null) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private QueryWrapper key(String tenant, String id) {
        DomainChecks.id(id);
        return scope(tenant).and(PAYMENT_CAPTURE_DO.ID.eq(id));
    }

    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create().where(PAYMENT_CAPTURE_DO.TENANT_ID.eq(tenant)).and(PAYMENT_CAPTURE_DO.DELETED.eq(0));
    }

    private QueryWrapper lineScope(String tenant, String id) {
        DomainChecks.id(id);
        return QueryWrapper.create().where(PAYMENT_CAPTURE_LINE_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_CAPTURE_LINE_DO.DELETED.eq(0)).and(PAYMENT_CAPTURE_LINE_DO.CAPTURE_ID.eq(id));
    }

    private void context(String tenant, boolean write) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || write && TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
    }
}
