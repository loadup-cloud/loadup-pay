package io.github.loadup.pay.customer.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantActionGrantRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for merchant action grants. Locks and versioned writes stay on SQL. */
public interface MerchantActionGrantRepository extends ListCrudRepository<MerchantActionGrantRow, String> {

    default Optional<MerchantActionGrantRow> findGrantById(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    default Optional<MerchantActionGrantRow> findGrant(
            String principalType, String principalId, String payeeMerchantId, String action) {
        return findByTenantIdAndPrincipalTypeAndPrincipalIdAndPayeeMerchantIdAndActionAndStateInAndDeletedFalse(
                TenantContext.tenantId(),
                principalType,
                principalId,
                payeeMerchantId,
                action,
                List.of("INIT", "ACTIVE"));
    }

    Optional<MerchantActionGrantRow> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);

    Optional<MerchantActionGrantRow>
            findByTenantIdAndPrincipalTypeAndPrincipalIdAndPayeeMerchantIdAndActionAndStateInAndDeletedFalse(
                    String tenantId,
                    String principalType,
                    String principalId,
                    String payeeMerchantId,
                    String action,
                    List<String> states);
}
