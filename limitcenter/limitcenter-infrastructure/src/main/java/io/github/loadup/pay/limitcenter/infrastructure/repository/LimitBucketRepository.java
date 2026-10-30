package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitBucketDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for limit buckets. Locking and versioned writes stay on SQL. */
public interface LimitBucketRepository extends ListCrudRepository<LimitBucketDO, String> {

    default Optional<LimitBucketDO> findBucket(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    Optional<LimitBucketDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);
}
