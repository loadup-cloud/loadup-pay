package io.github.loadup.pay.realtime.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradePaymentDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for payment projections. Locking reads stay on explicit SQL. */
public interface TradePaymentProjectionRepository extends ListCrudRepository<TradePaymentDO, String> {

    default Optional<TradePaymentDO> findPayment(String paymentId) {
        return findByTenantIdAndPaymentIdAndDeletedFalse(TenantContext.tenantId(), paymentId);
    }

    Optional<TradePaymentDO> findByTenantIdAndPaymentIdAndDeletedFalse(String tenantId, String paymentId);
}
