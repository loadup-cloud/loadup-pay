package io.github.loadup.pay.realtime.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeSummaryDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for refund summaries. Locking reads stay on explicit SQL. */
public interface TradeRefundSummaryRepository extends ListCrudRepository<TradeSummaryDO, String> {

    default Optional<TradeSummaryDO> findSummary(String orderId) {
        return findByTenantIdAndOrderIdAndDeletedFalse(TenantContext.tenantId(), orderId);
    }

    default Optional<TradeSummaryDO> findSummary(String orderId, String payeeMerchantId) {
        return findByTenantIdAndOrderIdAndPayeeMerchantIdAndDeletedFalse(
                TenantContext.tenantId(), orderId, payeeMerchantId);
    }

    Optional<TradeSummaryDO> findByTenantIdAndOrderIdAndDeletedFalse(String tenantId, String orderId);

    Optional<TradeSummaryDO> findByTenantIdAndOrderIdAndPayeeMerchantIdAndDeletedFalse(
            String tenantId, String orderId, String payeeMerchantId);
}
