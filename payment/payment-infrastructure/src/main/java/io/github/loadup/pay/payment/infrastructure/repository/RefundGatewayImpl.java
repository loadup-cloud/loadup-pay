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
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.RefundGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundCapacityReservation;
import io.github.loadup.pay.payment.domain.model.RefundRequestReference;
import io.github.loadup.pay.payment.domain.model.RefundRequestState;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentResultStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.RefundStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRefundCapacityDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundAllocationPlanDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundRequestDOMapper;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_REFUND_CAPACITY_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ALLOCATION_PLAN_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_REQUEST_DO;

@Repository
public class RefundGatewayImpl implements RefundGateway {
    private final RefundRequestDOMapper requests;
    private final RefundAllocationPlanDOMapper plans;
    private final RefundOrderDOMapper children;
    private final PaymentRefundCapacityDOMapper capacities;
    private final RefundStorageConverter converter;
    private final PaymentResultStorageConverter capacityConverter;
    private final PaymentAcceptanceCodec codec;
    private final JsonMapper json;

    public RefundGatewayImpl(RefundRequestDOMapper requests, RefundAllocationPlanDOMapper plans, RefundOrderDOMapper children,
                             PaymentRefundCapacityDOMapper capacities, RefundStorageConverter converter, PaymentResultStorageConverter capacityConverter,
                             PaymentAcceptanceCodec codec, JsonMapper json) {
        this.requests = requests;
        this.plans = plans;
        this.children = children;
        this.capacities = capacities;
        this.converter = converter;
        this.capacityConverter = capacityConverter;
        this.codec = codec;
        this.json = json.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    @Override
    public Optional<RefundRequestReference> reference(String tenant, String id) {
        requireReadTransaction(tenant);
        return read(() -> Optional.ofNullable(requests.selectOneByQuery(requestScope(tenant).and(REFUND_REQUEST_DO.ID.eq(id)))).map(converter::toReference));
    }

    @Override
    public Optional<RefundRequestReference> merchantReference(String tenant, String merchant, String number) {
        requireReadTransaction(tenant);
        return read(() -> Optional.ofNullable(requests.selectOneByQuery(requestScope(tenant)
                .and(REFUND_REQUEST_DO.PAYEE_MERCHANT_ID.eq(merchant)).and(REFUND_REQUEST_DO.MERCHANT_REFUND_NO.eq(number)))).map(converter::toReference));
    }

    @Override
    public StoredRefundRequest lock(String tenant, String id) {
        requireTransaction(tenant);
        if (requests.selectOneByQuery(requestScope(tenant).and(REFUND_REQUEST_DO.ID.eq(id)).forUpdate()) == null)
            throw new CommonException(NOT_FOUND);
        return load(tenant, id);
    }

    @Override
    public StoredRefundRequest load(String tenant, String id) {
        requireReadTransaction(tenant);
        return read(() -> {
            var row = requests.selectOneByQuery(requestScope(tenant).and(REFUND_REQUEST_DO.ID.eq(id)));
            if (row == null) throw new CommonException(NOT_FOUND);
            if (!"FIFO".equals(row.getAllocationPolicy()) || row.getPlanVersion() != 1
                    || !codec.checksum(row.getRefundTerms()).equals(row.getTermsHash()))
                throw new CommonException(SNAPSHOT_INVALID);
            var terms = json.readValue(row.getRefundTerms(), AdmittedOrderTermsDTO.class);
            if (!row.getAdmissionId().equals(terms.admissionId()) || !row.getOrderId().equals(terms.orderId())
                    || !row.getPayeeMerchantId().equals(terms.payeeMerchantId()) || !row.getCurrency().equals(terms.currency()))
                throw new CommonException(SNAPSHOT_INVALID);
            var fixed = plans.selectListByQuery(QueryWrapper.create().where(REFUND_ALLOCATION_PLAN_DO.TENANT_ID.eq(tenant))
                    .and(REFUND_ALLOCATION_PLAN_DO.DELETED.eq(0)).and(REFUND_ALLOCATION_PLAN_DO.REFUND_REQUEST_ID.eq(id))
                    .orderBy(REFUND_ALLOCATION_PLAN_DO.SEQUENCE_NO.asc()));
            var rows = children.selectListByQuery(QueryWrapper.create().where(REFUND_ORDER_DO.TENANT_ID.eq(tenant))
                    .and(REFUND_ORDER_DO.DELETED.eq(0)).and(REFUND_ORDER_DO.REFUND_REQUEST_ID.eq(id)));
            var byId = new HashMap<String, RefundOrderDO>();
            rows.forEach(child -> byId.put(child.getId(), child));
            if (fixed.isEmpty() || fixed.size() != rows.size() || byId.size() != rows.size())
                throw new CommonException(SNAPSHOT_INVALID);
            var allocations = new ArrayList<StoredRefundChild>();
            for (var item : fixed) {
                var child = byId.remove(item.getRefundId());
                if (child == null || !child.getRefundRequestId().equals(id) || !child.getPaymentId().equals(item.getPaymentId())
                        || !child.getAmountMinor().equals(item.getAmountMinor()) || !item.getPlanVersion().equals(row.getPlanVersion())
                        || !child.getCurrency().equals(row.getCurrency()) || item.getSequenceNo() != allocations.size() + 1)
                    throw new CommonException(SNAPSHOT_INVALID);
                allocations.add(converter.toChild(child, converter.toRefund(child), converter.toIdentity(child), item.getSequenceNo()));
            }
            var request = converter.toRequest(row, allocations.stream().map(StoredRefundChild::refund).toList());
            if (request.succeededMinor() != row.getSucceededMinor() || request.failedMinor() != row.getFailedMinor()
                    || request.unknownCount() != row.getUnknownCount()) throw new CommonException(SNAPSHOT_INVALID);
            return converter.toStored(row, request, allocations);
        });
    }

    @Override
    public List<PaymentRefundCapacity> lockCapacities(String tenant, String orderId) {
        requireTransaction(tenant);
        return read(() -> capacities.selectListByQuery(QueryWrapper.create().where(PAYMENT_REFUND_CAPACITY_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_REFUND_CAPACITY_DO.DELETED.eq(0)).and(PAYMENT_REFUND_CAPACITY_DO.ORDER_ID.eq(orderId))
                .orderBy(PAYMENT_REFUND_CAPACITY_DO.PAYMENT_ID.asc()).forUpdate()).stream().map(capacityConverter::toCapacity).toList());
    }

    @Override
    public void insertPlan(String tenant, StoredRefundRequest value, List<RefundCapacityReservation> reservations) {
        requireTransaction(tenant);
        if (value.request().state() != RefundRequestState.INIT || value.request().version() != 0 || value.request().planVersion() != 1
                || !codec.checksum(value.termsPayload()).equals(value.termsHash()))
            throw new CommonException(INVALID_STATE);
        var changes = new TreeMap<String, RefundCapacityReservation>();
        for (var r : reservations)
            if (changes.put(r.previous().paymentId(), r) != null) throw new CommonException(INVALID_STATE);
        if (changes.size() != value.allocations().size()) throw new CommonException(INVALID_STATE);
        for (var child : value.allocations()) {
            var r = changes.get(child.refund().paymentId());
            if (r == null || child.refund().state() != RefundState.INIT || child.refund().version() != 0
                    || !r.previous().orderId().equals(value.request().orderId())
                    || !r.previous().payeeMerchantId().equals(value.request().payeeMerchantId())
                    || !r.previous().succeededAmount().getCurrencyValue().equals(child.refund().amount().getCurrencyValue())
                    || r.reserved().reservedMinor() - r.previous().reservedMinor() != child.refund().amount().getCent())
                throw new CommonException(INVALID_STATE);
        }
        if (requests.insert(converter.toRequestDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
        for (var child : value.allocations()) {
            if (plans.insert(converter.toPlanDO(tenant, child)) != 1 || children.insert(converter.toChildDO(tenant, child)) != 1)
                throw new CommonException(CONCURRENT_UPDATE);
        }
        for (var r : changes.values()) {
            var before = r.previous();
            int changed = capacities.updateByQuery(converter.toReservationPatch(r.reserved()), true,
                    QueryWrapper.create().where(PAYMENT_REFUND_CAPACITY_DO.TENANT_ID.eq(tenant))
                            .and(PAYMENT_REFUND_CAPACITY_DO.DELETED.eq(0)).and(PAYMENT_REFUND_CAPACITY_DO.PAYMENT_ID.eq(before.paymentId()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.ORDER_ID.eq(before.orderId())).and(PAYMENT_REFUND_CAPACITY_DO.PAYEE_MERCHANT_ID.eq(before.payeeMerchantId()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.ROW_VERSION.eq(before.version()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.RESERVED_MINOR.eq(before.reservedMinor()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.REFUNDED_MINOR.eq(before.refundedMinor()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.SUCCEEDED_MINOR.eq(before.succeededAmount().getCent()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.SUCCEEDED_AT.eq(before.succeededAt())));
            if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private QueryWrapper requestScope(String tenant) {
        return QueryWrapper.create().where(REFUND_REQUEST_DO.TENANT_ID.eq(tenant)).and(REFUND_REQUEST_DO.DELETED.eq(0));
    }

    private void requireTransaction(String tenant) {
        requireReadTransaction(tenant);
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new CommonException(INVALID_STATE);
    }

    private void requireReadTransaction(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private <T> T read(Supplier<T> action) {
        try {
            return action.get();
        } catch (CommonException | org.springframework.dao.DataAccessException failure) {
            throw failure;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
