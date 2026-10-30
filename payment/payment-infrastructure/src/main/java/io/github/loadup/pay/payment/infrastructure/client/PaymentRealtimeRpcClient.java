package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.rpc.RealtimeRpcMapper;
import io.github.loadup.pay.realtime.rpc.proto.RealtimeRpc;
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
public class PaymentRealtimeRpcClient implements PaymentRealtimeClient {
    private final PaymentRealtimeProperties properties;
    private final PaymentServiceTokenReader tokens;
    private final RealtimeRpcMapper realtimeRpcMapper;
    @DubboReference(url = "tri://realtime-headless:50051")
    private RealtimeRpc realtimeRpc;

    public PaymentRealtimeRpcClient(RealtimeRpcMapper realtimeRpcMapper, JsonMapper mapper, PaymentRealtimeProperties properties,
                                    Clock clock) {
        this.realtimeRpcMapper = realtimeRpcMapper;
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public ProjectionAckDTO apply(String tenant, EventEnvelope<PaymentSnapshotEventDTO> event) {
        return exchange(tenant, () -> realtimeRpcMapper.fromWireProjectionAckDTO(realtimeRpc.payment(
                realtimeRpcMapper.toWirePayEventEnvelopeOfPaymentSnapshotEventDTO(event))));
    }

    @Override
    public ProjectionAckDTO applyRefund(String tenant, EventEnvelope<RefundSnapshotEventDTO> event) {
        return exchange(tenant, () -> realtimeRpcMapper.fromWireProjectionAckDTO(realtimeRpc.refund(
                realtimeRpcMapper.toWirePayEventEnvelopeOfRefundSnapshotEventDTO(event))));
    }

    @Override
    public ProjectionAckDTO applyRefundSummary(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(tenant, () -> realtimeRpcMapper.fromWireProjectionAckDTO(realtimeRpc.refundSummary(
                realtimeRpcMapper.toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(event))));
    }

    private ProjectionAckDTO exchange(String tenant, Supplier<ProjectionAckDTO> call) {
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
