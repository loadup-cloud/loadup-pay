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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.CheckoutConverter;
import io.github.loadup.pay.acquire.app.security.CashierActor;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Optional;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.order;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CashierSummaryServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(CheckoutConverter.class.getPackageName());
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);

    CashierSummaryService service(java.time.LocalDateTime expires) {
        return new CashierSummaryService(
                () -> new CashierActor("tenant", "session", "order", expires),
                orders,
                context.getBean(AcquireOrderConverter.class),
                context.getBean(CheckoutConverter.class),
                new OrderCreationTransactions(new UnitTransactions()),
                validation.getValidator(),
                clock(NOW));
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void summaryContainsNoContractCredentialOrMerchantIdentityAndOffersNoFakeChoices() {
        when(orders.findOrder("tenant", "order"))
                .thenReturn(
                        Optional.of(order(AcquireOrderState.PENDING, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10))));
        var dto = service(NOW.plusMinutes(5)).summary(new IdQuery("order"));
        assertEquals(2000, dto.paidAmountMinor());
        assertTrue(dto.choices().isEmpty());
        assertNull(dto.merchantDisplayName());
        var json = JsonUtil.toJson(dto);
        assertFalse(json.contains("contract"));
        assertFalse(json.contains("snapshot"));
        assertFalse(json.contains("merchantOrderNo"));
    }

    @Test
    void aCapabilityCannotReadAnotherOrderEvenWithAValidIdQuery() {
        assertThrows(CommonException.class, () -> service(NOW.plusMinutes(5)).summary(new IdQuery("other")));
        verifyNoInteractions(orders);
    }

    @Test
    void paidAndClosedOrdersAreReadableWithoutReopeningOrMutatingThem() {
        for (var state : List.of(AcquireOrderState.PAID, AcquireOrderState.CLOSED)) {
            when(orders.findOrder("tenant", "order"))
                    .thenReturn(Optional.of(order(state, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10))));
            var dto = service(NOW.plusMinutes(5)).summary(new IdQuery("order"));
            assertEquals(state.name(), dto.state().name());
            assertEquals(0, dto.availableToPayMinor());
        }
        verify(orders, never()).lockOrder(anyString(), anyString());
    }

    @Test
    void expiryReachedWhileLoadingTheOrderStillPreventsPresentation() {
        when(orders.findOrder("tenant", "order"))
                .thenReturn(Optional.of(order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10))));
        assertThrows(CommonException.class, () -> service(NOW).summary(new IdQuery("order")));
    }
}
