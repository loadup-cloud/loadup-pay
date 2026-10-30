package io.github.loadup.pay.reconciliation.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.components.rpc.dubbo.RpcExceptionCodes;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.PaymentCaptureRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentCaptureRpc;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentCaptures;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.function.Supplier;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_NOT_SEALED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;

@Component
public class PaymentCaptureRpcClient implements ReconciliationPaymentCaptures {
    @DubboReference(url = "tri://payment-headless:50051")
    private PaymentCaptureRpc paymentCaptureRpc;

    private final ReconciliationPaymentProperties properties;
    private final ReconciliationServiceTokenReader tokens;

    public PaymentCaptureRpcClient(ReconciliationPaymentProperties properties,
                                   JsonMapper mapper, @Qualifier("reconciliationClock") Clock clock) {
        this.properties = properties;
        this.tokens = new ReconciliationServiceTokenReader(mapper, clock);
    }

    @Override
    public PaymentCaptureDTO create(String tenant, PaymentCaptureCreateCommand command) {
        return call(tenant, () -> PaymentCaptureRpcMapper.INSTANCE.fromWire(paymentCaptureRpc.create(
                PaymentCaptureRpcMapper.INSTANCE.toWire(command))));
    }

    @Override
    public PaymentCaptureDTO retry(String tenant, io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand command) {
        return call(tenant, () -> PaymentCaptureRpcMapper.INSTANCE.fromWire(paymentCaptureRpc.retry(
                PaymentCaptureRpcMapper.INSTANCE.toWire(command))));
    }

    @Override
    public PaymentCaptureDTO detail(String tenant, String captureId) {
        return call(tenant, () -> PaymentCaptureRpcMapper.INSTANCE.fromWire(paymentCaptureRpc.detail(
                PaymentCaptureRpcMapper.INSTANCE.toWire(new IdQuery(captureId)))));
    }

    @Override
    public PageDTO<PaymentLedgerLineDTO> lines(String tenant, PaymentCaptureLinesQuery query) {
        return call(tenant, () -> PaymentCaptureRpcMapper.INSTANCE.fromWire(paymentCaptureRpc.lines(
                PaymentCaptureRpcMapper.INSTANCE.toWire(query))));
    }

    private <R> R call(String tenant, Supplier<R> request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!properties.tenantId().equals(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        try {
            var result = RpcTokenContext.call(
                    tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile()), request);
            if (result == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return result;
        } catch (RuntimeException failure) {
            throw new CommonException(switch (RpcExceptionCodes.resultCode(failure)) {
                case "PAYMENT_IDEMPOTENCY_CONFLICT" -> IDEMPOTENCY_CONFLICT;
                case "PAYMENT_INVALID_ARGUMENT" -> INVALID_ARGUMENT;
                case "PAYMENT_NOT_FOUND" -> NOT_FOUND;
                case "PAYMENT_INVALID_STATE" -> INPUT_NOT_SEALED;
                default -> DEPENDENCY_UNAVAILABLE;
            });
        }
    }
}
