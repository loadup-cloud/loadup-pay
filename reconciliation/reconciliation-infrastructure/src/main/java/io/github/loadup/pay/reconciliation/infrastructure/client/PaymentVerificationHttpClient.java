package io.github.loadup.pay.reconciliation.infrastructure.client;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.http.PaymentVerificationHttpService;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.client.support.PaymentEvidenceContracts;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentVerification;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import io.github.loadup.pay.security.ServiceAccessTokens;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentVerificationHttpClient implements ReconciliationPaymentVerification {
    private final ReconciliationPaymentProperties properties;
    private final ServiceAccessTokens tokens;
    private final PaymentVerificationHttpService paymentVerificationHttp;

    public PaymentVerificationHttpClient(
            PaymentVerificationHttpService paymentVerificationHttp,
            ReconciliationPaymentProperties properties,
            ServiceAccessTokens tokens) {
        this.paymentVerificationHttp = paymentVerificationHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public PaymentVerificationDTO verify(String tenant, PaymentVerificationQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (!properties.tenantId().equals(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        try {
            var value = HttpServiceResponses.requiredData(paymentVerificationHttp.verify(
                    "Bearer " + tokens.token(tenant, properties.serviceId(), properties.audience()), query));
            return PaymentEvidenceContracts.verification(value);
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
