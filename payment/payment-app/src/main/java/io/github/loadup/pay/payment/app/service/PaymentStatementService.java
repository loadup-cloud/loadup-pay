/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.channel.api.facade.ChannelStatementFacade;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelStatementRequest;
import io.github.loadup.pay.channel.api.model.ChannelStatementScope;
import io.github.loadup.pay.channel.api.model.VerifiedChannelStatement;
import io.github.loadup.pay.payment.app.converter.PaymentStatementConverter;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.facade.PaymentStatementFacade;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.STATEMENT_NOT_READY;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;

@Service
public class PaymentStatementService implements PaymentStatementFacade {
    private final PaymentActorProvider actors;
    private final ChannelBindingGateway bindings;
    private final ChannelStatementFacade channels;
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentStatementConverter converter;
    private final Validator validator;
    private final String reconciliationService;

    public PaymentStatementService(PaymentActorProvider actors, ChannelBindingGateway bindings, ChannelStatementFacade channels,
                                   PaymentAcceptanceTransactions transactions, PaymentStatementConverter converter, Validator validator,
                                   @Value("${loadup.modules.payment.reconciliation-service-id:reconciliation}") String reconciliationService) {
        this.actors = actors;
        this.bindings = bindings;
        this.channels = channels;
        this.transactions = transactions;
        this.converter = converter;
        this.validator = validator;
        this.reconciliationService = DomainChecks.id(reconciliationService);
    }

    @Override
    public PaymentStatementDTO download(PaymentStatementQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor == null || !reconciliationService.equals(actor.serviceId())
                || !actor.permissions().contains("pay:payment:reconciliation:statement:read"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        return transactions.withTenantContext(actor.tenantId(), () -> {
            // Resolve only the original immutable revision. A disabled route must not hide historical evidence.
            var binding = transactions.readSnapshot(actor.tenantId(), () -> bindings.findRevision(actor.tenantId(), query.bindingRevisionId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND)));
            if (!query.bindingRevisionId().equals(binding.id()) || binding.channel() != query.channel()
                    || !binding.accountScopeHash().equals(query.accountScopeHash()))
                throw new CommonException(INVALID_ARGUMENT);
            var request = new ChannelStatementRequest(new ChannelStatementScope(query.channel(), query.accountScopeHash(), query.billDate(), query.statementProfile()), query.maxBytes());
            final VerifiedChannelStatement source;
            try {
                source = channels.download(new ChannelExecutionContext(actor.tenantId(), binding), request);
            } catch (CommonException failure) {
                throw translate(failure);
            }
            if (source == null || !binding.id().equals(source.bindingRevisionId()) || !request.scope().equals(source.scope())
                    || source.size() > request.maxBytes()) throw new CommonException(SNAPSHOT_INVALID);
            return converter.toDTO(source);
        });
    }

    private CommonException translate(CommonException failure) {
        if (io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_READY.getCode().equals(failure.getResultCode().getCode()))
            return new CommonException(STATEMENT_NOT_READY);
        if (io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_FOUND.getCode().equals(failure.getResultCode().getCode()))
            return new CommonException(STATEMENT_NOT_FOUND);
        if (io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY.getCode().equals(failure.getResultCode().getCode()))
            return new CommonException(UNSUPPORTED_CAPABILITY);
        return new CommonException(DEPENDENCY_UNAVAILABLE);
    }
}
