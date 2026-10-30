package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitSettlementReceiptDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for limit settlement receipts. */
public interface LimitSettlementReceiptRepository extends ListCrudRepository<LimitSettlementReceiptDO, String> {

    default Optional<LimitSettlementReceiptDO> findReceipt(String operationId) {
        return findByTenantIdAndOperationIdAndDeletedFalse(TenantContext.tenantId(), operationId);
    }

    Optional<LimitSettlementReceiptDO> findByTenantIdAndOperationIdAndDeletedFalse(
            String tenantId, String operationId);
}
