package io.github.loadup.pay.realtime.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.realtime.infrastructure.dataobject.ProjectionReceiptDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for projection delivery receipts. */
public interface ProjectionReceiptRepository extends ListCrudRepository<ProjectionReceiptDO, String> {

    default Optional<ProjectionReceiptDO> find(String producer, String deliveryId) {
        return findByTenantIdAndProducerAndDeliveryIdAndDeletedFalse(
                TenantContext.tenantId(), producer, deliveryId);
    }

    Optional<ProjectionReceiptDO> findByTenantIdAndProducerAndDeliveryIdAndDeletedFalse(
            String tenantId, String producer, String deliveryId);
}
