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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.CheckoutConverter;
import io.github.loadup.pay.acquire.app.security.CashierActorProvider;
import io.github.loadup.pay.acquire.client.dto.CashierOrderDTO;
import io.github.loadup.pay.acquire.client.facade.CashierSummaryFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAUTHORIZED;

@Service
public class CashierSummaryService implements CashierSummaryFacade {
    private final CashierActorProvider actors;
    private final OrderCreationGateway orders;
    private final AcquireOrderConverter orderConverter;
    private final CheckoutConverter converter;
    private final OrderCreationTransactions transactions;
    private final Validator validator;
    private final Clock clock;

    public CashierSummaryService(
            CashierActorProvider actors,
            OrderCreationGateway orders,
            AcquireOrderConverter orderConverter,
            CheckoutConverter converter,
            OrderCreationTransactions transactions,
            Validator validator,
            @Qualifier("acquireClock") Clock clock) {
        this.actors = actors;
        this.orders = orders;
        this.orderConverter = orderConverter;
        this.converter = converter;
        this.transactions = transactions;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    public CashierOrderDTO summary(IdQuery query) {
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current(); // The HTTP provider always authenticates the opaque capability first.
        if (!actor.orderId().equals(query.id())) throw new CommonException(SESSION_UNAUTHORIZED);
        return transactions.read(actor.tenantId(), () -> {
            var stored = orders.findOrder(actor.tenantId(), actor.orderId())
                    .orElseThrow(() -> new CommonException(SESSION_UNAUTHORIZED));
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            if (!now.isBefore(actor.expiresAt())) throw new CommonException(SESSION_UNAUTHORIZED);
            // Paid/closed orders remain readable. Empty choices deliberately expose no unimplemented payment adapter.
            return converter.toSummary(orderConverter.toDTO(stored, now), stored.subject(), actor.expiresAt());
        });
    }
}
