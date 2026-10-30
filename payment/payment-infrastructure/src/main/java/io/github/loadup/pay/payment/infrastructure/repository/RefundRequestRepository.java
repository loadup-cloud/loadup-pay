package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundRequestDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for refund requests. Locking and conditional writes stay on SQL. */
public interface RefundRequestRepository extends ListCrudRepository<RefundRequestDO, String> {

    @Override
    default Optional<RefundRequestDO> findById(String id) {
        return findByTenantIdAndIdAndDeletedFalse(TenantContext.tenantId(), id);
    }

    default Optional<RefundRequestDO> findByMerchantReference(String payeeMerchantId, String merchantRefundNo) {
        return findByTenantIdAndPayeeMerchantIdAndMerchantRefundNoAndDeletedFalse(
                TenantContext.tenantId(), payeeMerchantId, merchantRefundNo);
    }

    Optional<RefundRequestDO> findByTenantIdAndIdAndDeletedFalse(String tenantId, String id);

    Optional<RefundRequestDO> findByTenantIdAndPayeeMerchantIdAndMerchantRefundNoAndDeletedFalse(
            String tenantId, String payeeMerchantId, String merchantRefundNo);
}
