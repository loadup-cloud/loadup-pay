package io.github.loadup.pay.payment.infrastructure.client;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_EXCEEDED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_RESERVATION_EXPIRED;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.infrastructure.config.PaymentLimitProperties;
import io.github.loadup.pay.security.ServiceAccessTokens;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentLimitHttpClient implements PaymentLimitClient {
    private final PaymentLimitProperties properties;
    private final ServiceAccessTokens tokens;
    private final LimitCenterHttpService limitCenterHttp;

    public PaymentLimitHttpClient(
            LimitCenterHttpService limitCenterHttp, ServiceAccessTokens tokens, PaymentLimitProperties properties) {
        this.limitCenterHttp = limitCenterHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public LimitReservationDTO reserve(String tenant, LimitReserveCommand command) {
        return exchange(tenant, authorization -> limitCenterHttp.reserve(authorization, command));
    }

    @Override
    public LimitExecutionPermitDTO begin(String tenant, LimitBeginExecutionCommand command) {
        return exchange(tenant, authorization -> limitCenterHttp.begin(authorization, command));
    }

    @Override
    public LimitExecutionVerificationDTO verify(String tenant, LimitExecutionVerifyCommand command) {
        return exchange(tenant, authorization -> limitCenterHttp.verify(authorization, command));
    }

    @Override
    public LimitReservationDTO confirm(String tenant, LimitConfirmCommand command) {
        return exchange(tenant, authorization -> limitCenterHttp.confirm(authorization, command));
    }

    @Override
    public LimitReleaseDTO release(String tenant, LimitReleaseCommand command) {
        return exchange(tenant, authorization -> limitCenterHttp.release(authorization, command));
    }

    @Override
    public LimitRestoreDTO restoreRefund(String tenant, LimitRefundRestoreCommand command) {
        return exchange(tenant, authorization -> limitCenterHttp.restoreRefund(authorization, command));
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
            var code =
                    switch (HttpServiceResponses.resultCode(failure)) {
                        case "LIMIT_EXCEEDED" -> LIMIT_EXCEEDED;
                        case "LIMIT_RESERVATION_EXPIRED" -> LIMIT_RESERVATION_EXPIRED;
                        case "LIMIT_EXECUTION_FENCED" -> EXECUTION_FENCED;
                        case "LIMIT_IDEMPOTENCY_CONFLICT" -> IDEMPOTENCY_CONFLICT;
                        default -> DEPENDENCY_UNAVAILABLE;
                    };
            throw new CommonException(code);
        }
    }

    private String credential(String tenant) {
        return tokens.token(tenant, properties.serviceId(), properties.audience());
    }
}
