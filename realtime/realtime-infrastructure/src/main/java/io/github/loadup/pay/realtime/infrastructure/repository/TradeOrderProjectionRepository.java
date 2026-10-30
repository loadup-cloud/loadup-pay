package io.github.loadup.pay.realtime.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeOrderDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for order projections. Locking reads stay on explicit SQL. */
public interface TradeOrderProjectionRepository extends ListCrudRepository<TradeOrderDO, String> {

    default Optional<TradeOrderDO> findOrder(String orderId) {
        return findByTenantIdAndOrderIdAndDeletedFalse(TenantContext.tenantId(), orderId);
    }

    default Optional<TradeOrderDO> findOrder(String orderId, String payeeMerchantId) {
        return findByTenantIdAndOrderIdAndPayeeMerchantIdAndDeletedFalse(
                TenantContext.tenantId(), orderId, payeeMerchantId);
    }

    Optional<TradeOrderDO> findByTenantIdAndOrderIdAndDeletedFalse(String tenantId, String orderId);

    Optional<TradeOrderDO> findByTenantIdAndOrderIdAndPayeeMerchantIdAndDeletedFalse(
            String tenantId, String orderId, String payeeMerchantId);
}
