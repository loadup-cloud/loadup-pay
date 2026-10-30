package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentInputDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for payment inputs. Locking and conditional writes stay on SQL. */
public interface PaymentInputRepository extends ListCrudRepository<PaymentInputDO, String> {

    @Override
    default Optional<PaymentInputDO> findById(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    default Optional<PaymentInputDO> findByPayment(String paymentId) {
        return findByTenantIdAndPaymentIdAndDeletedFalse(TenantContext.tenantId(), paymentId);
    }

    Optional<PaymentInputDO> findByTenantIdAndPaymentIdAndDeletedFalse(String tenantId, String paymentId);

    Optional<PaymentInputDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);
}
