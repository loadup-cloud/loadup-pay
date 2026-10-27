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

import io.github.loadup.commons.log.LogUtil;
import io.github.loadup.pay.payment.app.model.RecoveryHandlingOutcome;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Invocation measurements only; no database reads, tenant labels, financial decisions or private registries. */
@Component
public class RecoveryExecutionMetrics {
    public static final String EXECUTION = "loadup.payment.recovery.execution";
    public static final String INFLIGHT = "loadup.payment.recovery.inflight";
    private final Map<Kind, AtomicInteger> active = new EnumMap<>(Kind.class);
    private final Map<Kind, Map<Outcome, Timer>> timers = new EnumMap<>(Kind.class);

    public RecoveryExecutionMetrics(ObjectProvider<MeterRegistry> registries) {
        var registry = registries.getIfAvailable();
        for (var kind : Kind.values()) {
            var count = new AtomicInteger();
            active.put(kind, count);
            var measurements = new EnumMap<Outcome, Timer>(Outcome.class);
            if (registry != null) {
                Gauge.builder(INFLIGHT, count, AtomicInteger::get).tag("kind", kind.label).register(registry);
                for (var outcome : Outcome.values())
                    measurements.put(outcome, Timer.builder(EXECUTION).tags("kind", kind.label, "outcome", outcome.label)
                            .description("Recovery handler invocations, including replay and native retry").register(registry));
            }
            timers.put(kind, measurements);
        }
    }

    public void observe(Kind kind, Supplier<RecoveryHandlingOutcome> work) {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(work);
        var count = active.get(kind);
        long started = System.nanoTime();
        var outcome = Outcome.FAILED;
        count.incrementAndGet();
        try {
            var result = work.get();
            if (result != null) outcome = switch (result) {
                case COMPLETED -> Outcome.COMPLETED;
                case CONTINUED -> Outcome.CONTINUED;
                case SKIPPED -> Outcome.SKIPPED;
            };
        } finally {
            count.decrementAndGet();
            var timer = timers.get(kind).get(outcome);
            if (timer != null) {
                try {
                    timer.record(Math.max(0L, System.nanoTime() - started), TimeUnit.NANOSECONDS);
                } catch (RuntimeException failure) {
                    // Instrumentation must not replace the handler's original result or exception.
                    LogUtil.warn(RecoveryExecutionMetrics.class, "Recovery metric failed kind={} outcome={} type={}",
                            kind.label, outcome.label, failure.getClass().getSimpleName());
                }
            }
        }
    }

    public enum Kind {
        PAYMENT("payment"), REFUND("refund");
        private final String label;
        Kind(String label) { this.label = label; }
    }

    private enum Outcome {
        COMPLETED("completed"), CONTINUED("continued"), SKIPPED("skipped"), FAILED("failed");
        private final String label;
        Outcome(String label) { this.label = label; }
    }
}
