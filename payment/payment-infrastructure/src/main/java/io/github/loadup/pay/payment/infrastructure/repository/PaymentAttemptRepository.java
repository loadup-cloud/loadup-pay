package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentAttemptDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for payment attempts. Locking and conditional writes stay on SQL. */
public interface PaymentAttemptRepository extends ListCrudRepository<PaymentAttemptDO, String> {

    @Override
    default Optional<PaymentAttemptDO> findById(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    default Optional<PaymentAttemptDO> findByRequest(String paymentId, String requestKey) {
        return findByTenantIdAndPaymentIdAndRequestKeyAndDeletedFalse(
                TenantContext.tenantId(), paymentId, requestKey);
    }

    Optional<PaymentAttemptDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);

    Optional<PaymentAttemptDO> findByTenantIdAndPaymentIdAndRequestKeyAndDeletedFalse(
            String tenantId, String paymentId, String requestKey);
}
