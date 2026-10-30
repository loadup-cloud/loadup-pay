package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitExecutionGrantDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for limit execution grants. */
public interface LimitExecutionGrantRepository extends ListCrudRepository<LimitExecutionGrantDO, String> {

    default Optional<LimitExecutionGrantDO> findGrant(String operationId, long executionEpoch) {
        return findByTenantIdAndOperationIdAndExecutionEpochAndDeletedFalse(
                TenantContext.tenantId(), operationId, executionEpoch);
    }

    Optional<LimitExecutionGrantDO> findByTenantIdAndOperationIdAndExecutionEpochAndDeletedFalse(
            String tenantId, String operationId, long executionEpoch);
}
