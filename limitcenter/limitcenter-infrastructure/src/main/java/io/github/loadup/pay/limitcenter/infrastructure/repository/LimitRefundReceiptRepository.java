package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitRefundReceiptDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for limit refund receipts. */
public interface LimitRefundReceiptRepository extends ListCrudRepository<LimitRefundReceiptDO, String> {

    default Optional<LimitRefundReceiptDO> find(String refundId) {
        return findByTenantIdAndRefundIdAndDeletedFalse(TenantContext.tenantId(), refundId);
    }

    Optional<LimitRefundReceiptDO> findByTenantIdAndRefundIdAndDeletedFalse(String tenantId, String refundId);
}
