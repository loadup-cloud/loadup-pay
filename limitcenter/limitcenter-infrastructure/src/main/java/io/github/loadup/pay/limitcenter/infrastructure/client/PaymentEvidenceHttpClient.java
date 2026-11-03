package io.github.loadup.pay.limitcenter.infrastructure.client;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.limitcenter.app.port.PaymentEvidenceClient;
import io.github.loadup.pay.limitcenter.infrastructure.config.PaymentEvidenceProperties;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.http.PaymentHttpService;
import io.github.loadup.pay.security.ServiceAccessTokens;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentEvidenceHttpClient implements PaymentEvidenceClient {
    private final ServiceAccessTokens tokens;
    private final PaymentEvidenceProperties properties;
    private final PaymentHttpService paymentHttp;

    public PaymentEvidenceHttpClient(
            PaymentHttpService paymentHttp, ServiceAccessTokens tokens, PaymentEvidenceProperties properties) {
        this.paymentHttp = paymentHttp;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentSettlementFactsDTO paymentFacts(String tenant, String paymentId) {
        return exchange(tenant, authorization -> paymentHttp.facts(authorization, new IdQuery(paymentId)));
    }

    @Override
    public RefundSettlementFactsDTO refundFacts(String tenant, String refundId) {
        return exchange(tenant, authorization -> paymentHttp.refundFacts(authorization, new IdQuery(refundId)));
    }

    private <R> R exchange(String tenant, Function<String, SuccessResponse<R>> call) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            return HttpServiceResponses.requiredData(call.apply("Bearer " + credential(tenant)));
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential(String tenant) {
        return tokens.token(tenant, properties.serviceId(), properties.audience());
    }
}
