package io.github.loadup.pay.limitcenter.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.limitcenter.app.port.PaymentEvidenceClient;
import io.github.loadup.pay.limitcenter.infrastructure.config.PaymentEvidenceProperties;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentRpc;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;

@Component
public class PaymentEvidenceRpcClient implements PaymentEvidenceClient {
    @DubboReference(url = "tri://payment-headless:50051")
    private PaymentRpc paymentRpc;

    private final JsonMapper mapper;
    private final PaymentEvidenceProperties properties;
    private final Clock clock;

    public PaymentEvidenceRpcClient(JsonMapper mapper, PaymentEvidenceProperties properties,
                                    @Qualifier("limitcenterClock") Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentSettlementFactsDTO paymentFacts(String tenant, String paymentId) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentSettlementFactsDTO(paymentRpc.facts(
                PaymentRpcMapper.INSTANCE.toWireIdQuery(new IdQuery(paymentId)))));
    }

    @Override
    public RefundSettlementFactsDTO refundFacts(String tenant, String refundId) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWireRefundSettlementFactsDTO(paymentRpc.refundFacts(
                PaymentRpcMapper.INSTANCE.toWireIdQuery(new IdQuery(refundId)))));
    }

    private <R> R exchange(String tenant, Supplier<R> call) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            R response = RpcTokenContext.call(credential(tenant), call);
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential(String tenant) {
        // Preflight routing claims only. The receiving provider rebuilds identity from the same token.
        try (var input = Files.newInputStream(properties.tokenFile())) {
            byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            String token = new String(bytes, StandardCharsets.US_ASCII).strip();
            String[] parts = token.split("[.]", -1);
            if (parts.length != 3 || Arrays.stream(parts).anyMatch(part -> !part.matches("[A-Za-z0-9_-]+")))
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            var claims = mapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<Map<String, Object>>() {
                    });
            Object audience = claims.get("aud");
            boolean expectedAudience = properties.audience().equals(audience)
                    || audience instanceof Collection<?> values && values.contains(properties.audience());
            long now = clock.instant().getEpochSecond();
            if (!tenant.equals(claims.get("tenant_id"))
                    || !"SERVICE".equals(claims.get("principal_type"))
                    || !properties.serviceId().equals(claims.get("service_id"))
                    || !properties.serviceId().equals(claims.get("sub"))
                    || !expectedAudience
                    || epoch(claims.get("exp")) <= now
                    || claims.get("nbf") != null && epoch(claims.get("nbf")) > now)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return token;
        } catch (IOException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private long epoch(Object value) {
        if (!(value instanceof Number)) throw new CommonException(DEPENDENCY_UNAVAILABLE);
        return new BigDecimal(value.toString()).longValueExact();
    }
}
