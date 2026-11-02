/*
 * #%L
 * LoadUp Pay Reconciliation infrastructure
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.github.loadup.pay.reconciliation.infrastructure.client;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.rpc.PaymentVerificationRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentVerificationRpc;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentVerification;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import java.time.Clock;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentVerificationRpcClient implements ReconciliationPaymentVerification {
    private final ReconciliationPaymentProperties properties;
    private final ReconciliationServiceTokenReader tokens;
    private final PaymentVerificationRpcMapper paymentVerificationRpcMapper;

    @DubboReference
    private PaymentVerificationRpc paymentVerificationRpc;

    public PaymentVerificationRpcClient(
            PaymentVerificationRpcMapper paymentVerificationRpcMapper,
            ReconciliationPaymentProperties properties,
            JsonMapper mapper,
            Clock clock) {
        this.paymentVerificationRpcMapper = paymentVerificationRpcMapper;
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
            var value = RpcTokenContext.call(
                    tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile()),
                    () -> paymentVerificationRpcMapper.fromWire(
                            paymentVerificationRpc.verify(paymentVerificationRpcMapper.toWire(query))));
            if (value == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            return value;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
