package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitReservationDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for limit reservations. Locking and versioned writes stay on SQL. */
public interface LimitReservationRepository extends ListCrudRepository<LimitReservationDO, String> {

    default Optional<LimitReservationDO> findByOperation(String operationId) {
        return findByTenantIdAndOperationIdAndDeletedFalse(TenantContext.tenantId(), operationId);
    }

    Optional<LimitReservationDO> findByTenantIdAndOperationIdAndDeletedFalse(String tenantId, String operationId);
}
