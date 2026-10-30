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
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAUTHORIZED;

@Service
public class CashierSummaryService {
    private final CashierActorProvider actors;
    private final OrderCreationGateway orders;
    private final AcquireOrderConverter orderConverter;
    private final CheckoutConverter converter;
    private final OrderCreationTransactions transactions;
    private final Validator validator;
    private final Clock clock;
    private final CashierAccess access;
    private final CashierChoices choices;

    public CashierSummaryService(
            CashierActorProvider actors,
            OrderCreationGateway orders,
            AcquireOrderConverter orderConverter,
            CheckoutConverter converter,
            OrderCreationTransactions transactions,
            Validator validator,
            Clock clock, CashierAccess access, CashierChoices choices) {
        this.actors = actors;
        this.orders = orders;
        this.orderConverter = orderConverter;
        this.converter = converter;
        this.transactions = transactions;
        this.validator = validator;
        this.clock = clock;
        this.access = access;
        this.choices = choices;
    }

    public CashierOrderDTO summary(IdQuery query) {
        if (query == null || !validator.validate(query).isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var actor = actors.current(); // The HTTP provider always authenticates the opaque capability first.
        if (!actor.orderId().equals(query.id())) {
            throw new CommonException(SESSION_UNAUTHORIZED);
        }
        return transactions.withTenantContext(actor.tenantId(), () -> {
            var stored = orders.findOrder(actor.tenantId(), actor.orderId())
                    .orElseThrow(() -> new CommonException(SESSION_UNAUTHORIZED));
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            if (!now.isBefore(actor.expiresAt())) {
                throw new CommonException(SESSION_UNAUTHORIZED);
            }
            var session = access.read(actor, stored, "ORDER_SUMMARY");
            var available = session.session().state() == io.github.loadup.pay.acquire.domain.model.CheckoutSessionState.ACTIVE
                    ? choices.choices(stored, now) : java.util.Set.<io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO>of();
            return converter.toSummary(orderConverter.toDTO(stored, now), stored.subject(), actor.expiresAt(), available,
                    stored.order().paymentPolicy(), (long) stored.order().successfulPaymentCount() + stored.order().reservedPaymentCount());
        });
    }
}
