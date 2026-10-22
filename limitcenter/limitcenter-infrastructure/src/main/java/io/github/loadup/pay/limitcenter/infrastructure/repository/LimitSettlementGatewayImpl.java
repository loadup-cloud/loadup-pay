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
package io.github.loadup.pay.limitcenter.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitSettlementGateway;
import io.github.loadup.pay.limitcenter.domain.model.*;
import io.github.loadup.pay.limitcenter.infrastructure.converter.LimitSettlementStorageConverter;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static io.github.loadup.pay.limitcenter.infrastructure.dataobject.table.Tables.*;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.*;

@Repository
public class LimitSettlementGatewayImpl implements LimitSettlementGateway {
    private final LimitSettlementReceiptDOMapper receipts;
    private final LimitOperationGateDOMapper gates;
    private final LimitReservationItemDOMapper items;
    private final LimitSettlementStorageConverter converter;
    public LimitSettlementGatewayImpl(LimitSettlementReceiptDOMapper receipts, LimitOperationGateDOMapper gates,
            LimitReservationItemDOMapper items, LimitSettlementStorageConverter converter) {
        this.receipts = receipts; this.gates = gates; this.items = items; this.converter = converter;
    }

    @Override
    public Optional<LimitSettlementReceipt> findReceipt(String tenant, String operation) {
        var row = receipts.selectOneByQuery(QueryWrapper.create().where(LIMIT_SETTLEMENT_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(LIMIT_SETTLEMENT_RECEIPT_DO.DELETED.eq(0)).and(LIMIT_SETTLEMENT_RECEIPT_DO.OPERATION_ID.eq(operation)));
        if (row == null) return Optional.empty();
        try { return Optional.of(converter.toReceipt(row)); }
        catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }

    @Override
    public void insertReceipt(String tenant, LimitSettlementReceipt receipt) {
        transaction();
        if (receipts.insert(converter.toDO(tenant, receipt)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void fenceGate(String tenant, LimitOperationGate gate) {
        transaction();
        if (gate.state() != GateState.FENCED) throw new CommonException(SNAPSHOT_INVALID);
        var query = QueryWrapper.create().where(LIMIT_OPERATION_GATE_DO.TENANT_ID.eq(tenant))
                .and(LIMIT_OPERATION_GATE_DO.DELETED.eq(0)).and(LIMIT_OPERATION_GATE_DO.OPERATION_ID.eq(gate.operationId()));
        var row = gates.selectOneByQuery(query);
        if (row == null || !row.getPaymentId().equals(gate.paymentId()) || row.getRowVersion() == null)
            throw new CommonException(SNAPSHOT_INVALID);
        int changed = gates.updateByQuery(converter.toGatePatch(gate, Math.incrementExact(row.getRowVersion())), true,
                QueryWrapper.create().where(LIMIT_OPERATION_GATE_DO.TENANT_ID.eq(tenant))
                        .and(LIMIT_OPERATION_GATE_DO.DELETED.eq(0)).and(LIMIT_OPERATION_GATE_DO.ID.eq(row.getId()))
                        .and(LIMIT_OPERATION_GATE_DO.ROW_VERSION.eq(row.getRowVersion()))
                        .and(LIMIT_OPERATION_GATE_DO.STATE.in("INIT", "OPEN")));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateItems(String tenant, List<LimitReservationItem> before, List<LimitReservationItem> after) {
        transaction();
        if (before.size() != after.size()) throw new CommonException(SNAPSHOT_INVALID);
        var changed = new HashMap<String, LimitReservationItem>();
        for (var value : after) if (changed.put(value.id(), value) != null) throw new CommonException(SNAPSHOT_INVALID);
        for (var previous : before) {
            var next = changed.get(previous.id());
            if (next == null || !previous.reservationId().equals(next.reservationId()) || !previous.bucket().equals(next.bucket())
                    || previous.reserved() != next.reserved()) throw new CommonException(SNAPSHOT_INVALID);
            if (previous.equals(next)) continue;
            int count = items.updateByQuery(converter.toItemPatch(next), true, QueryWrapper.create()
                    .where(LIMIT_RESERVATION_ITEM_DO.TENANT_ID.eq(tenant)).and(LIMIT_RESERVATION_ITEM_DO.DELETED.eq(0))
                    .and(LIMIT_RESERVATION_ITEM_DO.ID.eq(previous.id())).and(LIMIT_RESERVATION_ITEM_DO.RESERVATION_ID.eq(previous.reservationId()))
                    .and(LIMIT_RESERVATION_ITEM_DO.RESERVED_VALUE.eq(previous.reserved()))
                    .and(LIMIT_RESERVATION_ITEM_DO.CONSUMED_VALUE.eq(previous.consumed()))
                    .and(LIMIT_RESERVATION_ITEM_DO.RESTORED_VALUE.eq(previous.restored())));
            if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
