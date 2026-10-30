package io.github.loadup.pay.reconciliation.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.rpc.PaymentVerificationRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentVerificationRpc;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentVerification;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;

@Component
public class PaymentVerificationRpcClient implements ReconciliationPaymentVerification {
    @DubboReference(url = "tri://payment-headless:50051")
    private PaymentVerificationRpc paymentVerificationRpc;

    private final ReconciliationPaymentProperties properties;
    private final ReconciliationServiceTokenReader tokens;

    public PaymentVerificationRpcClient(ReconciliationPaymentProperties properties,
                                        JsonMapper mapper, @Qualifier("reconciliationClock") Clock clock) {
        this.properties = properties;
        this.tokens = new ReconciliationServiceTokenReader(mapper, clock);
    }

    @Override
    public PaymentVerificationDTO verify(String tenant, PaymentVerificationQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!properties.tenantId().equals(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        try {
            var value = RpcTokenContext.call(
                    tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile()),
                    () -> PaymentVerificationRpcMapper.INSTANCE.fromWire(paymentVerificationRpc.verify(
                            PaymentVerificationRpcMapper.INSTANCE.toWire(query))));
            if (value == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return value;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
