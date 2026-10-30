package io.github.loadup.pay.reconciliation.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcExceptionCodes;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.rpc.PaymentStatementRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentStatementRpc;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentStatements;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.STATEMENT_NOT_READY;

@Component
public class PaymentStatementRpcClient implements ReconciliationPaymentStatements {
    @DubboReference(url = "tri://payment-headless:50051", timeout = 60000)
    private PaymentStatementRpc paymentStatementRpc;

    private final ReconciliationPaymentProperties properties;
    private final ReconciliationServiceTokenReader tokens;

    public PaymentStatementRpcClient(ReconciliationPaymentProperties properties,
                                     JsonMapper mapper, @Qualifier("reconciliationClock") Clock clock) {
        this.properties = properties;
        this.tokens = new ReconciliationServiceTokenReader(mapper, clock);
    }

    @Override
    public PaymentStatementDTO download(String tenant, PaymentStatementQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!properties.tenantId().equals(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        try {
            var value = RpcTokenContext.call(
                    tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile()),
                    () -> PaymentStatementRpcMapper.INSTANCE.fromWire(paymentStatementRpc.download(
                            PaymentStatementRpcMapper.INSTANCE.toWire(query))));
            if (value == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return value;
        } catch (RuntimeException failure) {
            throw new CommonException(switch (RpcExceptionCodes.resultCode(failure)) {
                case "PAYMENT_STATEMENT_NOT_READY" -> STATEMENT_NOT_READY;
                case "PAYMENT_STATEMENT_NOT_FOUND" -> STATEMENT_NOT_FOUND;
                case "PAYMENT_INVALID_ARGUMENT", "PAYMENT_UNSUPPORTED_CAPABILITY" -> INPUT_INVALID;
                default -> DEPENDENCY_UNAVAILABLE;
            });
        }
    }
}
