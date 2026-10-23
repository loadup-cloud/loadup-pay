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
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderFee;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderCreationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.AcquireOrderDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderContractSnapshotDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderCreateIntentDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderFeeDOMapper;
import io.github.loadup.commons.time.BusinessTime;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ACQUIRE_ORDER_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_CONTRACT_SNAPSHOT_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_CREATE_INTENT_DO;

@Repository
public class OrderCreationGatewayImpl implements OrderCreationGateway {
    private final OrderCreateIntentDOMapper intents;
    private final AcquireOrderDOMapper orders;
    private final OrderContractSnapshotDOMapper snapshots;
    private final OrderFeeDOMapper fees;
    private final OrderCreationStorageConverter converter;
    private final AcquireOrderConverter business;
    private final OrderCreationCodec codec;

    public OrderCreationGatewayImpl(
            OrderCreateIntentDOMapper intents,
            AcquireOrderDOMapper orders,
            OrderContractSnapshotDOMapper snapshots,
            OrderFeeDOMapper fees,
            OrderCreationStorageConverter converter,
            AcquireOrderConverter business,
            OrderCreationCodec codec) {
        this.intents = intents;
        this.orders = orders;
        this.snapshots = snapshots;
        this.fees = fees;
        this.converter = converter;
        this.business = business;
        this.codec = codec;
    }

    private QueryWrapper intentScope(String tenant) {
        return QueryWrapper.create()
                .where(ORDER_CREATE_INTENT_DO.TENANT_ID.eq(tenant))
                .and(ORDER_CREATE_INTENT_DO.DELETED.eq(0));
    }

    @Override
    public Optional<OrderCreateIntent> findIntent(String tenant, String payee, String merchantOrderNo) {
        var row = intents.selectOneByQuery(intentScope(tenant)
                .and(ORDER_CREATE_INTENT_DO.PAYEE_MERCHANT_ID.eq(payee))
                .and(ORDER_CREATE_INTENT_DO.MERCHANT_ORDER_NO.eq(merchantOrderNo)));
        return Optional.ofNullable(row).map(converter::toIntent);
    }

    @Override
    public OrderCreateIntent lockIntent(String tenant, String id) {
        requireTransaction();
        var row = intents.selectOneByQuery(
                intentScope(tenant).and(ORDER_CREATE_INTENT_DO.ID.eq(id)).forUpdate());
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        return converter.toIntent(row);
    }

    @Override
    public void insertIntent(String tenant, OrderCreateIntent intent) {
        requireTransaction();
        if (intents.insert(converter.toIntentDO(tenant, intent)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateIntent(String tenant, OrderCreateIntent value, long expectedVersion) {
        requireTransaction();
        int changed = intents.updateByQuery(
                converter.toIntentDO(tenant, value),
                true,
                intentScope(tenant)
                        .and(ORDER_CREATE_INTENT_DO.ID.eq(value.id()))
                        .and(ORDER_CREATE_INTENT_DO.ROW_VERSION.eq(expectedVersion))
                        .and(ORDER_CREATE_INTENT_DO.STATE.eq("INIT")));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public Optional<StoredAcquireOrder> findOrder(String tenant, String id) {
        var row = orders.selectOneByQuery(QueryWrapper.create()
                .where(ACQUIRE_ORDER_DO.TENANT_ID.eq(tenant))
                .and(ACQUIRE_ORDER_DO.ID.eq(id))
                .and(ACQUIRE_ORDER_DO.DELETED.eq(0)));
        if (row == null) return Optional.empty();
        try {
            var intentRow = intents.selectOneByQuery(intentScope(tenant).and(ORDER_CREATE_INTENT_DO.ORDER_ID.eq(id)));
            var snapshot = snapshots.selectOneByQuery(QueryWrapper.create()
                    .where(ORDER_CONTRACT_SNAPSHOT_DO.TENANT_ID.eq(tenant))
                    .and(ORDER_CONTRACT_SNAPSHOT_DO.ID.eq(row.getContractSnapshotId()))
                    .and(ORDER_CONTRACT_SNAPSHOT_DO.ORDER_ID.eq(id))
                    .and(ORDER_CONTRACT_SNAPSHOT_DO.DELETED.eq(0)));
            if (intentRow == null || snapshot == null || !"COMPLETED".equals(intentRow.getState()))
                throw new CommonException(SNAPSHOT_INVALID);
            var intent = converter.toIntent(intentRow);
            var checkout = "CHECKOUT".equals(intent.entryMode()) ? codec.decodeCheckout(intent) : null;
            var command = checkout == null ? codec.decodeIntent(intent) : business.toOrderCreate(checkout);
            String digest = checkout == null
                    ? codec.requestDigest(tenant, intent.callerScopeHash(), command)
                    : codec.checkoutDigest(tenant, intent.callerScopeHash(), checkout);
            var admission = codec.decodeAdmission(snapshot.getConfiguration(), snapshot.getPayloadHash());
            if (!digest.equals(intent.requestDigest())
                    || !id.equals(admission.orderId())
                    || !row.getPayeeMerchantId().equals(admission.payeeMerchantId())
                    || !row.getPayeeMerchantId().equals(command.payeeMerchantId())
                    || !row.getMerchantOrderNo().equals(command.merchantOrderNo())
                    || !row.getSubject().equals(command.subject())
                    || row.getAmountMinor() != command.amountMinor()
                    || !row.getCurrency().equals(command.currency())
                    || !row.getAdmissionId().equals(admission.admissionId())
                    || !intent.admissionId().equals(admission.admissionId())
                    || !intent.requestDigest().equals(admission.requestDigest())
                    || !row.getAdmittedAt().equals(BusinessTime.local(admission.admittedAt()))
                    || !Objects.equals(snapshot.getContractId(), admission.contractId())
                    || !Objects.equals(snapshot.getContractRevision(), admission.revision())
                    || !Objects.equals(snapshot.getContractGeneration(), admission.generation())
                    || !Objects.equals(snapshot.getSnapshotHash(), admission.snapshotHash())
                    || !Objects.equals(snapshot.getCompiledTermsHash(), admission.compiledTermsHash())
                    || !Objects.equals(
                    snapshot.getSchemaVersion(), admission.terms().schemaVersion())
                    || !Objects.equals(snapshot.getAdmissionDigest(), admission.requestDigest()))
                throw new CommonException(SNAPSHOT_INVALID);
            var policy = business.toPaymentPolicy(admission.terms().payment());
            var order = converter.toOrder(
                    row,
                    policy,
                    business.toUnpaidAction(admission.terms().order().unpaidAction()));
            return Optional.of(converter.toStored(row, snapshot, intent, order, business.toProof(admission)));
        } catch (RuntimeException corrupted) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public StoredAcquireOrder lockOrder(String tenant, String id) {
        requireTransaction();
        var row = orders.selectOneByQuery(QueryWrapper.create()
                .where(ACQUIRE_ORDER_DO.TENANT_ID.eq(tenant))
                .and(ACQUIRE_ORDER_DO.ID.eq(id))
                .and(ACQUIRE_ORDER_DO.DELETED.eq(0))
                .forUpdate());
        if (row == null) throw new CommonException(NOT_FOUND);
        // The held header lock serializes new sessions with future order lifecycle writes.
        return findOrder(tenant, id).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
    }

    @Override
    public void insertOrder(String tenant, StoredAcquireOrder value, OrderFee fee, String feePayload) {
        requireTransaction();
        if (!value.order().id().equals(fee.orderId()) || !value.order().amount().equals(fee.baseAmount()))
            throw new CommonException(SNAPSHOT_INVALID);
        if (snapshots.insert(converter.toSnapshotDO(tenant, value)) != 1
                || orders.insert(converter.toOrderDO(tenant, value)) != 1
                || fees.insert(converter.toFeeDO(tenant, fee, feePayload)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
