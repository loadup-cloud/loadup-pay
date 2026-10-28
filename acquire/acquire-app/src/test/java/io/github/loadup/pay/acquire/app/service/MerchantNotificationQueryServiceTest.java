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

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.acquire.app.converter.MerchantNotificationQueryConverter;
import io.github.loadup.pay.acquire.app.security.MerchantNotificationAccess;
import io.github.loadup.pay.acquire.app.security.MerchantNotificationActor;
import io.github.loadup.pay.acquire.client.query.MerchantNotificationPageQuery;
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationGateway;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationFilter;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantNotificationQueryServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MerchantNotificationQueryConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AtomicReference<MerchantNotificationActor> actor = new AtomicReference<>(new MerchantNotificationActor("tenant", "MERCHANT_APP", "app", "merchant",
            Set.of("pay:acquire:notification:read")));
    final MerchantNotificationGateway deliveries = mock(MerchantNotificationGateway.class);
    final MerchantNotificationQueryService service = new MerchantNotificationQueryService(new MerchantNotificationAccess(actor::get, "pay-ops"), deliveries,
            context.getBean(MerchantNotificationQueryConverter.class), new OrderCreationTransactions(new UnitTransactions()), validation.getValidator());

    @AfterEach
    void close() {
        validation.close();
        context.close();
    }

    @Test
    void detailProjectsOnlyMerchantSafeMetadata() {
        var original = MerchantNotificationOperationsServiceTest.delivery(NOW);
        when(deliveries.find("tenant", "delivery")).thenReturn(Optional.of(original));
        var result = service.detail(new IdQuery("delivery"));
        assertEquals("event", result.eventId());
        assertEquals(3, result.maxAttempts());
        assertEquals("INIT", result.state().name());
        String json = JsonUtil.toJson(result);
        for (String secret : List.of("frozen-body", "original-target", "original-key", "bodyDigest", "routeFingerprint", "nativeTaskId"))
            assertFalse(json.contains(secret));
    }

    @Test
    void foreignMerchantCannotReadAnExistingDeliveryOrPageAnotherScope() {
        when(deliveries.find("tenant", "delivery")).thenReturn(Optional.of(MerchantNotificationOperationsServiceTest.delivery(NOW)));
        actor.set(new MerchantNotificationActor("tenant", "MERCHANT_APP", "foreign-app", "other-merchant", Set.of("pay:acquire:notification:read")));
        assertSame(NOT_FOUND, assertThrows(CommonException.class, () -> service.detail(new IdQuery("delivery"))).getResultCode());
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.page(new MerchantNotificationPageQuery("merchant", null, null, null,
                null, null, 1, 20))).getResultCode());
        verify(deliveries, never()).page(anyString(), any());
    }

    @Test
    void pageUsesExplicitMerchantAndSourceTimeFiltersAndSharedPageContract() {
        var original = MerchantNotificationOperationsServiceTest.delivery(NOW);
        when(deliveries.page(eq("tenant"), any())).thenReturn(PageResult.of(List.of(original), 21, 2, 20));
        var page = service.page(new MerchantNotificationPageQuery("merchant", "order", null, null, null, null, 2, 20));
        var first = page.getData().iterator().next();
        assertEquals(1, page.getData().size());
        assertEquals(original.id(), first.id());
        var filter = org.mockito.ArgumentCaptor.forClass(MerchantNotificationFilter.class);
        verify(deliveries).page(eq("tenant"), filter.capture());
        assertEquals("merchant", filter.getValue().payeeMerchantId());
        assertEquals("order", filter.getValue().orderId());
        assertEquals(2, filter.getValue().page());
    }

    @Test
    void missingReadPermissionAndUnconfiguredServiceAreRejected() {
        actor.set(new MerchantNotificationActor("tenant", "MERCHANT", "merchant", "merchant", Set.of()));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.detail(new IdQuery("delivery"))).getResultCode());
        actor.set(new MerchantNotificationActor("tenant", "SERVICE", "payment", null, Set.of("pay:acquire:notification:manage")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.detail(new IdQuery("delivery"))).getResultCode());
        verifyNoInteractions(deliveries);
    }
}
