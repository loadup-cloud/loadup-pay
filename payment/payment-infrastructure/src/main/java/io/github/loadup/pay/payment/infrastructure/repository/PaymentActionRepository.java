package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentActionDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for payment actions. */
public interface PaymentActionRepository extends ListCrudRepository<PaymentActionDO, String> {

    @Override
    default Optional<PaymentActionDO> findById(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    Optional<PaymentActionDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);
}
