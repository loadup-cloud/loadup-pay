package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentControlReceiptDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for payment control receipts. */
public interface PaymentControlReceiptRepository extends ListCrudRepository<PaymentControlReceiptDO, String> {

    default Optional<PaymentControlReceiptDO> findReceipt(String kind, String targetId, String requestKey) {
        return findByTenantIdAndKindAndTargetIdAndRequestKeyAndDeletedFalse(
                TenantContext.tenantId(), kind, targetId, requestKey);
    }

    default Optional<PaymentControlReceiptDO> findNoCollectionReceipt(String paymentId) {
        return findFirstByTenantIdAndKindAndTargetIdAndNoCollectionEvidenceIdIsNotNullAndDeletedFalseOrderByIdAsc(
                TenantContext.tenantId(), "PAYMENT_CLOSE", paymentId);
    }

    Optional<PaymentControlReceiptDO> findByTenantIdAndKindAndTargetIdAndRequestKeyAndDeletedFalse(
            String tenantId, String kind, String targetId, String requestKey);

    Optional<PaymentControlReceiptDO> findFirstByTenantIdAndKindAndTargetIdAndNoCollectionEvidenceIdIsNotNullAndDeletedFalseOrderByIdAsc(
            String tenantId, String kind, String targetId);
}
