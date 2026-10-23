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
package io.github.loadup.pay.realtime.app.service;

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.realtime.app.converter.TradeProjectionConverter;
import io.github.loadup.pay.realtime.app.security.*;
import io.github.loadup.pay.realtime.client.query.*;
import io.github.loadup.pay.realtime.domain.gateway.TradeQueryGateway;
import io.github.loadup.pay.realtime.domain.model.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantTradeQueryServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TradeProjectionConverter.class.getPackageName());
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final TradeQueryGateway queries = mock(TradeQueryGateway.class);
    final AtomicReference<RealtimeActor> actor = new AtomicReference<>(new RealtimeActor("tenant", null, "merchant", Set.of("pay:realtime:trade:read")));
    final MerchantTradeQueryService service = new MerchantTradeQueryService(actor::get, queries, context.getBean(TradeProjectionConverter.class),
            new RealtimeTransactions(new RealtimeFixtures.Transactions()), validation.getValidator());
    TradeOrderProjection order() {
        return new TradeOrderProjection("order", "merchant", "M1", DomainChecks.amount(1000, "CNY"), 0, 0, "PENDING",
                RealtimeFixtures.NOW.minusMinutes(1), RealtimeFixtures.NOW, new SourceVersion("acquire", "AcquireOrder", "order", 2), RealtimeFixtures.NOW);
    }
    @BeforeEach void setup() {
        when(queries.findOrder("tenant", "merchant", "order")).thenReturn(Optional.of(order()));
        when(queries.findRefundSummary("tenant", "merchant", "order")).thenReturn(Optional.empty());
    }
    @AfterEach void close() { context.close(); validation.close(); }
    @Test void detailDoesNotFabricateZeroRefundTotalsWhenTheIndependentSummaryIsMissing() {
        var value = service.detail(new TradeDetailQuery("merchant", "order"));
        assertNull(value.refundSummary()); assertNull(value.order().refundedMinor()); assertNull(value.order().netReceivedMinor());
        assertNull(value.order().refundSourceVersion()); assertFalse(value.order().amountsConsistent());
    }
    @Test void summaryAheadOfOrderIsExposedWithoutClampingOrRewritingEitherSource() {
        when(queries.findRefundSummary("tenant", "merchant", "order")).thenReturn(Optional.of(new TradeRefundSummary("order", "merchant", "CNY",
                100, 0, new SourceVersion("payment", "OrderRefundSummary", "order", 5), RealtimeFixtures.NOW)));
        var value = service.detail(new TradeDetailQuery("merchant", "order")).order();
        assertEquals(-100, value.netReceivedMinor()); assertEquals(100, value.refundedMinor()); assertFalse(value.amountsConsistent());
        assertEquals(2, value.orderSourceVersion()); assertEquals(5, value.refundSourceVersion());
    }
    @Test void merchantFilterCannotAuthorizeAnotherMerchant() {
        assertSame(QUERY_FORBIDDEN, assertThrows(CommonException.class,
                () -> service.detail(new TradeDetailQuery("other-merchant", "order"))).getResultCode());
        verifyNoInteractions(queries);
    }
    @Test void serviceCredentialsCannotBeUsedForUnscopedMerchantQueries() {
        actor.set(new RealtimeActor("tenant", "payment", null, Set.of("pay:realtime:trade:read")));
        assertThrows(CommonException.class, () -> service.detail(new TradeDetailQuery("merchant", "order")));
        verifyNoInteractions(queries);
    }
    @Test void paymentPaginationDoesNotRequireAnOrderProjectionAndUsesTrustedMerchantScope() {
        when(queries.payments(eq("tenant"), any())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            TradePageFilter filter = call.getArgument(1); assertEquals("merchant", filter.payeeMerchantId()); assertEquals(20, filter.size());
            return new PageResult<>(List.of(), 0, 1, 20);
        });
        assertTrue(service.payments(new TradePaymentPageQuery("merchant", null, null, null, null, null, null)).getData().isEmpty());
        verify(queries, never()).findOrder(anyString(), anyString(), anyString());
    }
    @Test void missingOrderDoesNotBecomeASyntheticHeaderFromAChildPayment() {
        when(queries.findOrder("tenant", "merchant", "order")).thenReturn(Optional.empty());
        assertSame(NOT_FOUND, assertThrows(CommonException.class,
                () -> service.detail(new TradeDetailQuery("merchant", "order"))).getResultCode());
    }
    @Test void pageBoundsAreRejectedInsteadOfSilentlyTruncatingChildCollections() {
        assertThrows(CommonException.class, () -> new TradePaymentPageQuery("merchant", null, null, null, null, 0, 20));
        assertThrows(CommonException.class, () -> new TradeRefundPageQuery("merchant", null, null, null, null, null, 1, 101));
    }
    @Test void reversedTimeRangeCannotReachTheQueryGateway() {
        assertThrows(CommonException.class, () -> service.payments(new TradePaymentPageQuery("merchant", null, null,
                BusinessTime.external(RealtimeFixtures.NOW), BusinessTime.external(RealtimeFixtures.NOW.minusDays(1)), 1, 20)));
        verifyNoInteractions(queries);
    }
}
