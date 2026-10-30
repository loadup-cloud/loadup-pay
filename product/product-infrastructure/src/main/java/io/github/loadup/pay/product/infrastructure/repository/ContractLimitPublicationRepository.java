package io.github.loadup.pay.product.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.product.infrastructure.dataobject.ContractLimitPublicationDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for published contract policies. Locks and versioned writes stay on SQL. */
public interface ContractLimitPublicationRepository extends ListCrudRepository<ContractLimitPublicationDO, String> {

    default Optional<ContractLimitPublicationDO> findPublication(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    default Optional<ContractLimitPublicationDO> findPublished(String contractId, int contractRevision) {
        return findByTenantIdAndContractIdAndContractRevisionAndDeletedFalse(
                TenantContext.tenantId(), contractId, contractRevision);
    }

    default Optional<ContractLimitPublicationDO> findActiveByMerchant(String payeeMerchantId) {
        return findByTenantIdAndPayeeMerchantIdAndDeletedFalse(TenantContext.tenantId(), payeeMerchantId);
    }

    default Optional<ContractLimitPublicationDO> findActiveByPolicy(String policyId) {
        return findByTenantIdAndPolicyIdAndDeletedFalse(TenantContext.tenantId(), policyId);
    }

    Optional<ContractLimitPublicationDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);

    Optional<ContractLimitPublicationDO> findByTenantIdAndContractIdAndContractRevisionAndDeletedFalse(
            String tenantId, String contractId, int contractRevision);

    Optional<ContractLimitPublicationDO> findByTenantIdAndPayeeMerchantIdAndDeletedFalse(
            String tenantId, String payeeMerchantId);

    Optional<ContractLimitPublicationDO> findByTenantIdAndPolicyIdAndDeletedFalse(String tenantId, String policyId);
}
