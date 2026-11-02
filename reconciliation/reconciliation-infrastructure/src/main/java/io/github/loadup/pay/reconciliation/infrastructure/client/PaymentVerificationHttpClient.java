package io.github.loadup.pay.reconciliation.infrastructure.client;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.payment.client.support.PaymentEvidenceContracts;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.client.http.PaymentVerificationHttpService;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentVerification;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentVerificationHttpClient implements ReconciliationPaymentVerification {
    private final ReconciliationPaymentProperties properties;
    private final ReconciliationServiceTokenReader tokens;
    private final PaymentVerificationHttpService paymentVerificationHttp;

    public PaymentVerificationHttpClient(
            PaymentVerificationHttpService paymentVerificationHttp,
            ReconciliationPaymentProperties properties,
            JsonMapper mapper,
            Clock clock) {
        this.paymentVerificationHttp = paymentVerificationHttp;
        this.properties = properties;
        this.tokens = new ReconciliationServiceTokenReader(mapper, clock);
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
            var value = HttpServiceResponses.requiredData(paymentVerificationHttp.verify("Bearer " +
                    tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile()), query));
            return PaymentEvidenceContracts.verification(value);
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
