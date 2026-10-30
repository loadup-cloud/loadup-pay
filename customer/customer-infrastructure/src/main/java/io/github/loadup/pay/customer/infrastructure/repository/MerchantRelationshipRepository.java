package io.github.loadup.pay.customer.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantRelationshipRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for merchant relationships. Locks and versioned writes stay on SQL. */
public interface MerchantRelationshipRepository extends ListCrudRepository<MerchantRelationshipRow, String> {

    default Optional<MerchantRelationshipRow> findRelationship(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    default Optional<MerchantRelationshipRow> findLive(String parentMerchantId, String childMerchantId) {
        return findByTenantIdAndParentMerchantIdAndChildMerchantIdAndRelationTypeAndStateInAndDeletedFalse(
                TenantContext.tenantId(),
                parentMerchantId,
                childMerchantId,
                "MANAGEMENT",
                List.of("INIT", "ACTIVE"));
    }

    Optional<MerchantRelationshipRow> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);

    Optional<MerchantRelationshipRow>
            findByTenantIdAndParentMerchantIdAndChildMerchantIdAndRelationTypeAndStateInAndDeletedFalse(
                    String tenantId,
                    String parentMerchantId,
                    String childMerchantId,
                    String relationType,
                    List<String> states);
}
