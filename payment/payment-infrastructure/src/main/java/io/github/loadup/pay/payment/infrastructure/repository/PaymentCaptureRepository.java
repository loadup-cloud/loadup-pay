package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentCaptureDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for reconciliation captures. Locking and conditional writes stay on SQL. */
public interface PaymentCaptureRepository extends ListCrudRepository<PaymentCaptureDO, String> {

    @Override
    default Optional<PaymentCaptureDO> findById(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    default Optional<PaymentCaptureDO> findByRequest(String callerServiceId, String requestKey) {
        return findByTenantIdAndCallerServiceIdAndRequestKeyAndDeletedFalse(
                TenantContext.tenantId(), callerServiceId, requestKey);
    }

    Optional<PaymentCaptureDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);

    Optional<PaymentCaptureDO> findByTenantIdAndCallerServiceIdAndRequestKeyAndDeletedFalse(
            String tenantId, String callerServiceId, String requestKey);
}
