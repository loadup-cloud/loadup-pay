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

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.domain.gateway.OrderFeeGateway;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderFeeStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderFeeDOMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;

@Repository
public class OrderFeeGatewayImpl implements OrderFeeGateway {
    private final OrderFeeDOMapper fees;
    private final OrderFeeStorageConverter converter;
    private final JsonMapper mapper;

    public OrderFeeGatewayImpl(OrderFeeDOMapper fees, OrderFeeStorageConverter converter, JsonMapper mapper) {
        this.fees = fees;
        this.converter = converter;
        this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    @Override
    public void confirmPaid(String tenant, AcquireOrder paid, OrderFeePolicy expectedPolicy, LocalDateTime confirmedAt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (paid.state() != AcquireOrderState.PAID || paid.paidAmountMinor() != paid.amount().getCent() || confirmedAt == null)
            throw new CommonException(INVALID_STATE);
        var row = fees.selectOneByQuery(QueryWrapper.create()
                .where(ORDER_FEE_DO.TENANT_ID.eq(tenant)).and(ORDER_FEE_DO.DELETED.eq(0))
                .and(ORDER_FEE_DO.ORDER_ID.eq(paid.id())).and(ORDER_FEE_DO.FEE_TYPE.eq("ORDER_AMOUNT")).forUpdate());
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        OrderFee current;
        try {
            var policy = mapper.readValue(row.getFeeRuleSnapshot(), OrderFeePolicy.class);
            current = converter.toFee(row, policy);
            if (!expectedPolicy.equals(policy) || !current.baseAmount().equals(paid.amount())
                    || row.getRowVersion() == null || row.getRowVersion() < 0
                    || (current.state() == FeeState.CONFIRMED) != (row.getConfirmedAt() != null))
                throw new CommonException(SNAPSHOT_INVALID);
        } catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
        if (current.state() == FeeState.CONFIRMED) return;
        int changed = fees.updateByQuery(converter.toConfirmationPatch(current.confirm(), Math.incrementExact(row.getRowVersion()), confirmedAt), true,
                QueryWrapper.create().where(ORDER_FEE_DO.TENANT_ID.eq(tenant)).and(ORDER_FEE_DO.DELETED.eq(0))
                        .and(ORDER_FEE_DO.ID.eq(current.id())).and(ORDER_FEE_DO.STATE.eq("INIT"))
                        .and(ORDER_FEE_DO.ROW_VERSION.eq(row.getRowVersion())));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
}
