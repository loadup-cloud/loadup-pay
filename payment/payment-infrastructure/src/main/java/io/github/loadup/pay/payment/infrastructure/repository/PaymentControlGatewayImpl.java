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
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.infrastructure.converter.*;
import io.github.loadup.pay.payment.infrastructure.mapper.*;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentControlReceiptDO;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.*;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

@Repository
public class PaymentControlGatewayImpl implements PaymentControlGateway {
    private final PaymentOrderDOMapper payments;
    private final PaymentParentGateDOMapper parents;
    private final PaymentGateDOMapper gates;
    private final PaymentControlReceiptDOMapper receipts;
    private final PaymentAcceptanceStorageConverter converter;
    private final PaymentControlStorageConverter receiptConverter;
    private final PaymentAcceptanceCodec codec;

    public PaymentControlGatewayImpl(
            PaymentOrderDOMapper payments,
            PaymentParentGateDOMapper parents,
            PaymentGateDOMapper gates,
            PaymentControlReceiptDOMapper receipts,
            PaymentAcceptanceStorageConverter converter,
            PaymentControlStorageConverter receiptConverter,
            PaymentAcceptanceCodec codec) {
        this.payments = payments;
        this.parents = parents;
        this.gates = gates;
        this.receipts = receipts;
        this.converter = converter;
        this.receiptConverter = receiptConverter;
        this.codec = codec;
    }

    private QueryWrapper paymentScope(String tenant) {
        return QueryWrapper.create()
                .where(PAYMENT_ORDER_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_ORDER_DO.DELETED.eq(0));
    }

    private QueryWrapper receiptScope(String tenant) {
        return QueryWrapper.create()
                .where(PAYMENT_CONTROL_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_CONTROL_RECEIPT_DO.DELETED.eq(0));
    }

    @Override
    public Optional<StoredPaymentAcceptance> lockPayment(String tenant, String paymentId) {
        requireTransaction();
        var row = payments.selectOneByQuery(
                paymentScope(tenant).and(PAYMENT_ORDER_DO.ID.eq(paymentId)).forUpdate());
        if (row == null) return Optional.empty();
        try {
            var stored = converter.toStored(row, converter.toPayment(row));
            codec.decode(stored);
            return Optional.of(stored);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void updatePayment(String tenant, StoredPaymentAcceptance value, long expectedVersion) {
        requireTransaction();
        if (value.payment().version() <= expectedVersion) throw new CommonException(INVALID_STATE);
        int changed = payments.updateByQuery(
                converter.toPaymentDO(tenant, value),
                true,
                paymentScope(tenant)
                        .and(PAYMENT_ORDER_DO.ID.eq(value.payment().id()))
                        .and(PAYMENT_ORDER_DO.ROW_VERSION.eq(expectedVersion)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateParent(String tenant, StoredPaymentGate value, long expectedVersion) {
        requireTransaction();
        checkGateVersion(value, expectedVersion);
        int changed = parents.updateByQuery(
                converter.toParentDO(tenant, value),
                true,
                QueryWrapper.create()
                        .where(PAYMENT_PARENT_GATE_DO.TENANT_ID.eq(tenant))
                        .and(PAYMENT_PARENT_GATE_DO.DELETED.eq(0))
                        .and(PAYMENT_PARENT_GATE_DO.ID.eq(value.id()))
                        .and(PAYMENT_PARENT_GATE_DO.ORDER_ID.eq(value.gate().orderId()))
                        .and(PAYMENT_PARENT_GATE_DO.ROW_VERSION.eq(expectedVersion)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateGate(String tenant, StoredPaymentGate value, long expectedVersion) {
        requireTransaction();
        checkGateVersion(value, expectedVersion);
        int changed = gates.updateByQuery(
                converter.toGateDO(tenant, value),
                true,
                QueryWrapper.create()
                        .where(PAYMENT_GATE_DO.TENANT_ID.eq(tenant))
                        .and(PAYMENT_GATE_DO.DELETED.eq(0))
                        .and(PAYMENT_GATE_DO.ID.eq(value.id()))
                        .and(PAYMENT_GATE_DO.PAYMENT_ID.eq(value.gate().paymentId()))
                        .and(PAYMENT_GATE_DO.ROW_VERSION.eq(expectedVersion)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void checkGateVersion(StoredPaymentGate value, long expectedVersion) {
        if (value.rowVersion() != Math.incrementExact(expectedVersion)
                || value.gate().state() != GateState.FENCED) throw new CommonException(INVALID_STATE);
    }

    @Override
    public Optional<PaymentControlReceipt> findReceipt(String tenant, String kind, String targetId, String requestKey) {
        return receipt(receipts.selectOneByQuery(receiptScope(tenant)
                .and(PAYMENT_CONTROL_RECEIPT_DO.KIND.eq(kind))
                .and(PAYMENT_CONTROL_RECEIPT_DO.TARGET_ID.eq(targetId))
                .and(PAYMENT_CONTROL_RECEIPT_DO.REQUEST_KEY.eq(requestKey))));
    }

    @Override
    public Optional<PaymentControlReceipt> findNoCollectionReceipt(String tenant, String paymentId) {
        return receipt(receipts.selectOneByQuery(receiptScope(tenant)
                .and(PAYMENT_CONTROL_RECEIPT_DO.KIND.eq("PAYMENT_CLOSE"))
                .and(PAYMENT_CONTROL_RECEIPT_DO.TARGET_ID.eq(paymentId))
                .and(PAYMENT_CONTROL_RECEIPT_DO.NO_COLLECTION_EVIDENCE_ID.isNotNull())
                .limit(1)));
    }

    private Optional<PaymentControlReceipt> receipt(PaymentControlReceiptDO row) {
        try {
            return Optional.ofNullable(row).map(receiptConverter::toReceipt);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertReceipt(String tenant, PaymentControlReceipt value) {
        requireTransaction();
        if (receipts.insert(receiptConverter.toReceiptDO(tenant, value)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public PaymentChildrenStatus childrenStatus(String tenant, String orderId) {
        requireTransaction();
        long active = payments.selectCountByQuery(paymentScope(tenant)
                .and(PAYMENT_ORDER_DO.ORDER_ID.eq(orderId))
                .and(PAYMENT_ORDER_DO.STATE.in("INIT", "PROCESSING", "UNKNOWN", "CLOSING")));
        long missingProof = payments.selectCountByQuery(paymentScope(tenant)
                .and(PAYMENT_ORDER_DO.ORDER_ID.eq(orderId))
                .and(PAYMENT_ORDER_DO.STATE.in("SUCCEEDED", "FAILED", "CLOSED"))
                .and(PAYMENT_ORDER_DO.TERMINAL_EVIDENCE_ID.isNull()));
        long uncertain = payments.selectCountByQuery(paymentScope(tenant)
                .and(PAYMENT_ORDER_DO.ORDER_ID.eq(orderId))
                .and(PAYMENT_ORDER_DO.STATE.in("PROCESSING", "UNKNOWN", "CLOSING"))
                .and(PAYMENT_ORDER_DO.ACTIVE_ATTEMPT_ID.isNotNull()));
        return new PaymentChildrenStatus(Math.addExact(active, missingProof), Math.addExact(uncertain, missingProof));
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
