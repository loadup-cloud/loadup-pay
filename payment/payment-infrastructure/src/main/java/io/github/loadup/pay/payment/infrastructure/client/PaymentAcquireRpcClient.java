package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.acquire.rpc.AcquireRpcMapper;
import io.github.loadup.pay.acquire.rpc.proto.AcquireRpc;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentAcquireProperties;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;

@Component
public class PaymentAcquireRpcClient implements PaymentAcquireClient {
    @DubboReference(url = "tri://acquire-headless:50051")
    private AcquireRpc acquireRpc;

    private final PaymentAcquireProperties properties;
    private final PaymentServiceTokenReader tokens;

    public PaymentAcquireRpcClient(JsonMapper mapper, PaymentAcquireProperties properties,
                                   @Qualifier("paymentClock") Clock clock) {
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentOutcomeAckDTO accept(String tenant, EventEnvelope<PaymentOutcomeEventDTO> event) {
        return exchange(tenant, () -> AcquireRpcMapper.INSTANCE.fromWirePaymentOutcomeAckDTO(acquireRpc.paymentOutcome(
                AcquireRpcMapper.INSTANCE.toWirePayEventEnvelopeOfPaymentOutcomeEventDTO(event))));
    }

    @Override
    public FeeRefundAckDTO acceptFeeRefund(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(tenant, () -> AcquireRpcMapper.INSTANCE.fromWireFeeRefundAckDTO(acquireRpc.feeRefund(
                AcquireRpcMapper.INSTANCE.toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(event))));
    }

    @Override
    public RefundNotificationAckDTO acceptRefundNotification(String tenant, EventEnvelope<RefundMerchantEventDTO> event) {
        return exchange(tenant, () -> AcquireRpcMapper.INSTANCE.fromWireRefundNotificationAckDTO(
                acquireRpc.refundNotification(
                        AcquireRpcMapper.INSTANCE.toWirePayEventEnvelopeOfRefundMerchantEventDTO(event))));
    }

    private <R> R exchange(String tenant, Supplier<R> call) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var token = tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
            var response = RpcTokenContext.call(token, call);
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
