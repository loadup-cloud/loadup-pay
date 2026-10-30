package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentCloseEvidenceDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for payment close evidence. */
public interface PaymentCloseEvidenceRepository extends ListCrudRepository<PaymentCloseEvidenceDO, String> {

    @Override
    default Optional<PaymentCloseEvidenceDO> findById(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    Optional<PaymentCloseEvidenceDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);
}
