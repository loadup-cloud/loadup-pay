package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyRevisionDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for policy revisions. Tenant identity comes from the execution context. */
public interface LimitPolicyRevisionRepository extends ListCrudRepository<LimitPolicyRevisionDO, String> {

    default Optional<LimitPolicyRevisionDO> findRevision(String policyId, int policyVersion) {
        return findByTenantIdAndPolicyIdAndPolicyVersionAndDeletedFalse(
                TenantContext.tenantId(), policyId, policyVersion);
    }

    Optional<LimitPolicyRevisionDO> findByTenantIdAndPolicyIdAndPolicyVersionAndDeletedFalse(
            String tenantId, String policyId, int policyVersion);
}
