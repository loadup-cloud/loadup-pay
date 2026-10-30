package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundTransactionClaimDO;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC reads for refund transaction claims. */
public interface RefundTransactionClaimRepository extends ListCrudRepository<RefundTransactionClaimDO, String> {

    default Optional<RefundTransactionClaimDO> findTransaction(
            String channel, String accountScopeHash, String channelRefundTransactionNo) {
        return findByChannelAndAccountScopeHashAndChannelRefundTransactionNoAndDeletedFalse(
                channel, accountScopeHash, channelRefundTransactionNo);
    }

    default Optional<RefundTransactionClaimDO> findClaim(String refundId) {
        return findByTenantIdAndRefundIdAndDeletedFalse(TenantContext.tenantId(), refundId);
    }

    Optional<RefundTransactionClaimDO>
            findByChannelAndAccountScopeHashAndChannelRefundTransactionNoAndDeletedFalse(
                    String channel, String accountScopeHash, String channelRefundTransactionNo);

    Optional<RefundTransactionClaimDO> findByTenantIdAndRefundIdAndDeletedFalse(String tenantId, String refundId);
}
