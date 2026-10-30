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
    private final PaymentAcquireProperties properties;
    private final PaymentServiceTokenReader tokens;
    private final AcquireRpcMapper acquireRpcMapper;
    @DubboReference(url = "tri://acquire-headless:50051")
    private AcquireRpc acquireRpc;

    public PaymentAcquireRpcClient(AcquireRpcMapper acquireRpcMapper, JsonMapper mapper, PaymentAcquireProperties properties,
                                   Clock clock) {
        this.acquireRpcMapper = acquireRpcMapper;
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentOutcomeAckDTO accept(String tenant, EventEnvelope<PaymentOutcomeEventDTO> event) {
        return exchange(tenant, () -> acquireRpcMapper.fromWirePaymentOutcomeAckDTO(acquireRpc.paymentOutcome(
                acquireRpcMapper.toWirePayEventEnvelopeOfPaymentOutcomeEventDTO(event))));
    }

    @Override
    public FeeRefundAckDTO acceptFeeRefund(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(tenant, () -> acquireRpcMapper.fromWireFeeRefundAckDTO(acquireRpc.feeRefund(
                acquireRpcMapper.toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(event))));
    }

    @Override
    public RefundNotificationAckDTO acceptRefundNotification(String tenant, EventEnvelope<RefundMerchantEventDTO> event) {
        return exchange(tenant, () -> acquireRpcMapper.fromWireRefundNotificationAckDTO(
                acquireRpc.refundNotification(
                        acquireRpcMapper.toWirePayEventEnvelopeOfRefundMerchantEventDTO(event))));
    }

    private <R> R exchange(String tenant, Supplier<R> call) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            var token = tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
            var response = RpcTokenContext.call(token, call);
            if (response == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
