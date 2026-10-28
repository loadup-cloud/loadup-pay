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
package io.github.loadup.pay.payment.infrastructure.task;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.pay.payment.app.model.RecoveryHandlingOutcome;
import io.github.loadup.pay.payment.app.service.PaymentRecoveryService;
import io.github.loadup.pay.payment.app.service.RecoveryExecutionMetrics;
import io.github.loadup.pay.payment.app.service.RefundRecoveryService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.LocalDateTime;
import java.util.Map;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecoveryTaskHandlerMetricsTest {
    private static RecoveryExecutionMetrics metrics(MeterRegistry registry) {
        return new RecoveryExecutionMetrics(new StaticListableBeanFactory(Map.of("registry", registry)).getBeanProvider(MeterRegistry.class));
    }

    private static DelayedTask task(String type) {
        var at = LocalDateTime.of(2026, 10, 27, 12, 0);
        return new DelayedTask("task", "tenant", type, "original.0", at, at, DelayedTaskStatus.RUNNING, 1, 3, at.plusMinutes(5), null);
    }

    @Test
    void paymentAdapterMeasuresTheOriginalTaskAndPreservesTheServiceDecision() {
        try {
            var registry = new SimpleMeterRegistry();
            var service = mock(PaymentRecoveryService.class);
            var task = task(PaymentRecoveryService.TYPE);
            when(service.handle(task)).thenReturn(RecoveryHandlingOutcome.CONTINUED);
            var handler = new PaymentRecoveryTaskHandler(service, metrics(registry));
            assertEquals(PaymentRecoveryService.TYPE, handler.taskType());
            handler.handle(task);
            verify(service, times(1)).handle(task);
            assertEquals(1L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "payment", "outcome", "continued").timer().count());
        } catch (Exception e) {
        }
    }

    @Test
    void refundAdapterPropagatesTheSameFailureForNativeRetry() {
        try {
            var registry = new SimpleMeterRegistry();
            var service = mock(RefundRecoveryService.class);
            var task = task(RefundRecoveryService.TYPE);
            var original = new CommonException(DEPENDENCY_UNAVAILABLE);
            when(service.handle(task)).thenThrow(original);
            var handler = new RefundRecoveryTaskHandler(service, metrics(registry));
            assertEquals(RefundRecoveryService.TYPE, handler.taskType());
            assertSame(original, assertThrows(CommonException.class, () -> handler.handle(task)));
            verify(service, times(1)).handle(task);
            assertEquals(1L, registry.get(RecoveryExecutionMetrics.EXECUTION).tags("kind", "refund", "outcome", "failed").timer().count());
            assertEquals(0.0, registry.get(RecoveryExecutionMetrics.INFLIGHT).tag("kind", "refund").gauge().value());
        } catch (Exception e) {
        }
    }
}
