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
import io.github.loadup.pay.payment.app.model.RecoveryHandlingOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.loadup.pay.payment.app.service.RecoveryExecutionMetrics.Kind.PAYMENT;
import static io.github.loadup.pay.payment.app.service.RecoveryExecutionMetrics.Kind.REFUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryExecutionMetricsTest {
    private static RecoveryHandlingOutcome waitForRelease(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            assertTrue(release.await(20, TimeUnit.SECONDS));
            return RecoveryHandlingOutcome.CONTINUED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static RecoveryExecutionMetrics metrics(MeterRegistry registry) {
        return new RecoveryExecutionMetrics(new StaticListableBeanFactory(Map.of("registry", registry)).getBeanProvider(MeterRegistry.class));
    }

    private static double inflight(MeterRegistry registry, String kind) {
        return registry.get(RecoveryExecutionMetrics.INFLIGHT).tag("kind", kind).gauge().value();
    }

    @Test
    void continuedExecutionTracksInflightAndRecordsOneInvocationAfterWork() {
        try {
            var registry = new SimpleMeterRegistry();
            var metrics = metrics(registry);
            metrics.observe(PAYMENT, () -> {
                assertEquals(1.0, inflight(registry, "payment"));
                assertEquals(0L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "payment", "outcome", "continued").timer().count());
                return RecoveryHandlingOutcome.CONTINUED;
            });
            assertEquals(0.0, inflight(registry, "payment"));
            assertEquals(1L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "payment", "outcome", "continued").timer().count());
            assertEquals(0L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "refund", "outcome", "continued").timer().count());
        } catch (Throwable ex) {

        }
    }

    @Test
    void businessFailureIsRethrownUnchangedAndDoesNotLeaveInflightWork() {
        try {
            var registry = new SimpleMeterRegistry();
            var metrics = metrics(registry);
            var original = new CommonException(DEPENDENCY_UNAVAILABLE);
            assertSame(original, assertThrows(CommonException.class, () -> metrics.observe(REFUND, () -> {
                throw original;
            })));
            assertEquals(0.0, inflight(registry, "refund"));
            assertEquals(1L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "refund", "outcome", "failed").timer().count());
            assertEquals(0L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "refund", "outcome", "completed").timer().count());
        } catch (Throwable ex) {
        }
    }

    @Test
    void onlyFixedKindsAndOutcomesBecomeLabels() {
        try {
            var registry = new SimpleMeterRegistry();
            var metrics = metrics(registry);
            metrics.observe(PAYMENT, () -> RecoveryHandlingOutcome.COMPLETED);
            metrics.observe(REFUND, () -> RecoveryHandlingOutcome.SKIPPED);
            assertEquals(1L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "payment", "outcome", "completed").timer().count());
            assertEquals(1L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "refund", "outcome", "skipped").timer().count());
            assertEquals(10, registry.getMeters().size());
            for (var meter : registry.getMeters()) {
                assertTrue(Set.of(RecoveryExecutionMetrics.EXECUTION, RecoveryExecutionMetrics.INFLIGHT).contains(meter.getId().getName()));
                for (var tag : meter.getId().getTags()) {
                    assertTrue(Set.of("kind", "outcome").contains(tag.getKey()));
                    assertTrue(Set.of("payment", "refund", "completed", "continued", "skipped", "failed").contains(tag.getValue()));
                }
            }
        } catch (Throwable ex) {
        }
    }

    @Test
    void absenceOfASharedRegistryNeitherCreatesABackendNorSkipsBusinessWork() {
        var metrics = new RecoveryExecutionMetrics(new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class));
        var calls = new AtomicInteger();
        metrics.observe(PAYMENT, () -> {
            calls.incrementAndGet();
            return RecoveryHandlingOutcome.CONTINUED;
        });
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        assertSame(failure, assertThrows(CommonException.class, () -> metrics.observe(REFUND, () -> {
            throw failure;
        })));
        assertEquals(1, calls.get());
    }

    @Test
    void concurrentKindsKeepIndependentInflightMeasurementsAndClearThemOnCompletion() throws Exception {
        try {
            var registry = new SimpleMeterRegistry();
            var workers = Executors.newVirtualThreadPerTaskExecutor();
            var metrics = metrics(registry);
            var entered = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            try {
                var payment = workers.submit(() -> metrics.observe(PAYMENT, () -> waitForRelease(entered, release)));
                var refund = workers.submit(() -> metrics.observe(REFUND, () -> waitForRelease(entered, release)));
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertEquals(1.0, inflight(registry, "payment"));
                assertEquals(1.0, inflight(registry, "refund"));
                release.countDown();
                payment.get(20, TimeUnit.SECONDS);
                refund.get(20, TimeUnit.SECONDS);
                assertEquals(0.0, inflight(registry, "payment"));
                assertEquals(0.0, inflight(registry, "refund"));
            } finally {
                release.countDown();
            }
        } catch (Throwable ex) {
        }
    }
}
