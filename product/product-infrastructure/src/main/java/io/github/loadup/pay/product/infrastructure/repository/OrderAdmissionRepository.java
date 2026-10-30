package io.github.loadup.pay.product.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.product.infrastructure.dataobject.OrderAdmissionDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for order admissions. Tenant identity comes from the execution context. */
public interface OrderAdmissionRepository extends ListCrudRepository<OrderAdmissionDO, String> {

    default Optional<OrderAdmissionDO> findByOrder(String orderId) {
        return findByTenantIdAndOrderIdAndDeletedFalse(TenantContext.tenantId(), orderId);
    }

    default Optional<OrderAdmissionDO> findAdmissionById(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    Optional<OrderAdmissionDO> findByTenantIdAndOrderIdAndDeletedFalse(String tenantId, String orderId);

    Optional<OrderAdmissionDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);
}
