package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitReservationItemDO;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for limit reservation items. */
public interface LimitReservationItemRepository extends ListCrudRepository<LimitReservationItemDO, String> {

    default List<LimitReservationItemDO> items(String reservationId) {
        return findByTenantIdAndReservationIdAndDeletedFalse(TenantContext.tenantId(), reservationId);
    }

    List<LimitReservationItemDO> findByTenantIdAndReservationIdAndDeletedFalse(
            String tenantId, String reservationId);
}
