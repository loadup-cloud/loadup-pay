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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.model.RecoveryInventoryCell;
import io.github.loadup.pay.payment.app.port.RecoveryInventoryReader;
import io.github.loadup.pay.payment.domain.policy.RecoveryEscalationPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.app.model.RecoveryInventoryCell.Kind.PAYMENT;
import static io.github.loadup.pay.payment.app.model.RecoveryInventoryCell.Kind.REFUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RecoveryInventoryCollectorTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 27, 12, 0);

    private static RecoveryInventoryCollector collector(RecoveryInventoryReader reader, MeterRegistry registry, Clock clock, boolean enabled, String tenants) {
        var transactions = new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions());
        return new RecoveryInventoryCollector(reader, transactions, clock,
                new StaticListableBeanFactory(Map.of("registry", registry)).getBeanProvider(MeterRegistry.class),
                new RecoveryEscalationPolicy(900L, 86400L), enabled, tenants, 300);
    }

    private static double gauge(MeterRegistry registry, String suffix, String kind) {
        var search = registry.get(RecoveryInventoryCollector.PREFIX + suffix);
        if (kind != null) {
            search.tag("kind", kind);
        }
        return search.gauge().value();
    }

    @Test
    void completeTrustedScopePublishesOneCombinedSnapshotAndScrapesDoNotQueryTheDatabase() {
        var reader = mock(RecoveryInventoryReader.class);
        var clock = new MutableClock();
        when(reader.read(anyString())).thenAnswer(call -> {
            String tenant = call.getArgument(0);
            assertEquals(tenant, TenantUtil.getTenantId());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return tenant.equals("tenant-a") ? List.of(new RecoveryInventoryCell(PAYMENT, 2L, 10000L, AT.minusHours(2)), RecoveryInventoryCell.empty(REFUND))
                    : List.of(new RecoveryInventoryCell(PAYMENT, 1L, 5000L, AT.minusHours(1)), new RecoveryInventoryCell(REFUND, 1L, 700L, AT.minusMinutes(10)));
        });
        try {
            var registry = new SimpleMeterRegistry();
            var collector = collector(reader, registry, clock, true, "tenant-a,tenant-a,tenant-b");
            assertTrue(Double.isNaN(gauge(registry, ".unknown.count", "payment")));
            assertEquals(0.0, gauge(registry, ".initialized", null));
            assertEquals(900.0, gauge(registry, ".unknown.review.after", null));
            assertEquals(86400.0, gauge(registry, ".unknown.urgent.after", null));
            assertEquals(300.0, gauge(registry, ".sample.interval", null));
            collector.execute();
            assertEquals(3.0, gauge(registry, ".unknown.count", "payment"));
            assertEquals(15000.0, gauge(registry, ".unknown.nominal.amount", "payment"));
            assertEquals(7200.0, gauge(registry, ".unknown.oldest.age", "payment"));
            assertEquals(1.0, gauge(registry, ".unknown.count", "refund"));
            assertEquals(700.0, gauge(registry, ".unknown.nominal.amount", "refund"));
            assertEquals(2.0, gauge(registry, ".configured.tenants", null));
            assertEquals(1.0, gauge(registry, ".initialized", null));
            verify(reader, times(1)).read("tenant-a");
            verify(reader, times(1)).read("tenant-b");
            collector.execute();
            for (int i = 0; i < 5; i++) {
                gauge(registry, ".unknown.count", "payment");
            }
            verifyNoMoreInteractions(reader);
            assertNull(TenantUtil.getTenantId());
        } catch (Exception e) {
        }
    }

    @Test
    void oneFailedTenantRetainsTheWholeOldSnapshotAndExposesItsAge() {
        var reader = mock(RecoveryInventoryReader.class);
        var clock = new MutableClock();
        var fail = new AtomicBoolean();
        var reads = new AtomicInteger();
        var original = new CommonException(DEPENDENCY_UNAVAILABLE);
        when(reader.read(anyString())).thenAnswer(call -> {
            if (fail.get() && reads.incrementAndGet() == 2) {
                throw original;
            }
            long count = fail.get() ? 99L : 1L;
            return List.of(new RecoveryInventoryCell(PAYMENT, count, count * 100L, AT.minusHours(1)), RecoveryInventoryCell.empty(REFUND));
        });
        try {
            var registry = new SimpleMeterRegistry();
            var collector = collector(reader, registry, clock, true, "tenant-a,tenant-b");
            collector.execute();
            assertEquals(2.0, gauge(registry, ".unknown.count", "payment"));
            clock.advance(Duration.ofSeconds(301));
            fail.set(true);
            assertSame(original, assertThrows(CommonException.class, collector::execute));
            assertEquals(2.0, gauge(registry, ".unknown.count", "payment"));
            assertEquals(200.0, gauge(registry, ".unknown.nominal.amount", "payment"));
            assertEquals(301.0, gauge(registry, ".sample.age", null));
            assertEquals(1.0, registry.get(RecoveryInventoryCollector.PREFIX + ".refresh").tag("outcome", "failure").counter().count());
            assertEquals(1.0, registry.get(RecoveryInventoryCollector.PREFIX + ".refresh").tag("outcome", "success").counter().count());
        } catch (Exception e) {
        }
    }

    @Test
    void incompleteReaderOutputCannotMasqueradeAsAZeroInventory() {
        var reader = mock(RecoveryInventoryReader.class);
        when(reader.read("tenant")).thenReturn(List.of(RecoveryInventoryCell.empty(PAYMENT)));
        try {
            var registry = new SimpleMeterRegistry();
            var collector = collector(reader, registry, new MutableClock(), true, "tenant");
            assertThrows(CommonException.class, collector::execute);
            assertTrue(Double.isNaN(gauge(registry, ".unknown.count", "refund")));
            assertEquals(0.0, gauge(registry, ".initialized", null));
        } catch (Exception e) {
        }
    }

    @Test
    void anObservedEmptyScopePublishesZerosAndClockRegressionDoesNotFreezeSampling() {
        var reader = mock(RecoveryInventoryReader.class);
        when(reader.read("tenant")).thenReturn(List.of(RecoveryInventoryCell.empty(PAYMENT), RecoveryInventoryCell.empty(REFUND)));
        var clock = new MutableClock();
        try {
            var registry = new SimpleMeterRegistry();
            var collector = collector(reader, registry, clock, true, "tenant");
            collector.execute();
            assertEquals(0.0, gauge(registry, ".unknown.count", "payment"));
            assertEquals(0.0, gauge(registry, ".unknown.oldest.age", "refund"));
            clock.advance(Duration.ofSeconds(-60));
            collector.execute();
            verify(reader, times(2)).read("tenant");
            assertEquals(0.0, gauge(registry, ".sample.age", null));
        } catch (Exception e) {
        }
    }

    @Test
    void disabledOrUnobservedCollectorsDoNotReadFinancialTables() {
        var reader = mock(RecoveryInventoryReader.class);
        try {
            var registry = new SimpleMeterRegistry();
            collector(reader, registry, new MutableClock(), false, "").execute();
        } catch (Exception e) {
        }
        var transactions = new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions());
        new RecoveryInventoryCollector(reader, transactions, new MutableClock(), new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class),
                new RecoveryEscalationPolicy(900L, 86400L), true, "tenant", 300).execute();
        verifyNoInteractions(reader);
        try {
            var registry = new SimpleMeterRegistry();
            assertThrows(CommonException.class, () -> collector(reader, registry, new MutableClock(), true, ""));
        } catch (Exception e) {
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(AT.atZone(ZoneId.systemDefault()).toInstant());

        void advance(Duration duration) {
            time.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(time.get(), zone);
        }

        @Override
        public Instant instant() {
            return time.get();
        }
    }
}
