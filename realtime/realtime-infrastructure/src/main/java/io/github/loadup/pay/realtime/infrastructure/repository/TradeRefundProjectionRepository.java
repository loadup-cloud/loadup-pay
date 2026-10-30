package io.github.loadup.pay.realtime.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeRefundDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for refund projections. Locking reads stay on explicit SQL. */
public interface TradeRefundProjectionRepository extends ListCrudRepository<TradeRefundDO, String> {

    default Optional<TradeRefundDO> findRefund(String refundId) {
        return findByTenantIdAndRefundIdAndDeletedFalse(TenantContext.tenantId(), refundId);
    }

    Optional<TradeRefundDO> findByTenantIdAndRefundIdAndDeletedFalse(String tenantId, String refundId);
}
