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
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.log.LogUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.scheduler.ScheduledTask;
import io.github.loadup.pay.payment.app.model.RecoveryInventoryCell;
import io.github.loadup.pay.payment.app.port.RecoveryInventoryReader;
import io.github.loadup.pay.payment.domain.policy.RecoveryEscalationPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Independent opt-in inventory sampler. A metrics scrape never performs database or financial work.
 */
@Service
public class RecoveryInventoryCollector implements ScheduledTask {
    public static final String PREFIX = "loadup.payment.recovery.inventory";
    private final RecoveryInventoryReader reader;
    private final PaymentAcceptanceTransactions transactions;
    private final Clock clock;
    private final boolean enabled;
    private final Set<String> tenants;
    private final long intervalSeconds;
    private final boolean hasRegistry;
    private final RecoveryEscalationPolicy escalation;
    private final Counter success;
    private final Counter failure;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final AtomicBoolean collecting = new AtomicBoolean();
    private Instant lastAttempt;

    public RecoveryInventoryCollector(RecoveryInventoryReader reader, PaymentAcceptanceTransactions transactions,
                                      Clock clock, ObjectProvider<MeterRegistry> registries, RecoveryEscalationPolicy escalation,
                                      @Value("${loadup.modules.payment.recovery-inventory.enabled:false}") boolean enabled,
                                      @Value("${loadup.modules.payment.recovery-inventory.tenant-ids:}") String tenantIds,
                                      @Value("${loadup.modules.payment.recovery-inventory.interval-seconds:300}") long intervalSeconds) {
        this.reader = reader;
        this.transactions = transactions;
        this.clock = clock;
        this.enabled = enabled;
        this.escalation = escalation;
        if (intervalSeconds < 1 || intervalSeconds > 86400) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        this.intervalSeconds = intervalSeconds;
        this.tenants = Set.copyOf(Arrays.stream(tenantIds.split(",", -1)).map(String::trim).filter(value -> !value.isEmpty()).toList());
        if (tenants.size() > 100 || enabled && tenants.isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        tenants.forEach(DomainChecks::id);
        var registry = registries.getIfAvailable();
        hasRegistry = registry != null;
        if (registry == null) {
            success = null;
            failure = null;
        } else {
            success = registry.counter(PREFIX + ".refresh", "outcome", "success");
            failure = registry.counter(PREFIX + ".refresh", "outcome", "failure");
            Gauge.builder(PREFIX + ".enabled", this, value -> value.enabled ? 1.0 : 0.0).register(registry);
            Gauge.builder(PREFIX + ".configured.tenants", this, value -> value.tenants.size()).register(registry);
            Gauge.builder(PREFIX + ".initialized", this, value -> value.snapshot.get() == null ? 0.0 : 1.0).register(registry);
            Gauge.builder(PREFIX + ".sample.age", this, RecoveryInventoryCollector::sampleAge).baseUnit("seconds").register(registry);
            Gauge.builder(PREFIX + ".sample.interval", this, value -> value.intervalSeconds).baseUnit("seconds").register(registry);
            Gauge.builder(PREFIX + ".unknown.review.after", this, value -> value.escalation.reviewAfterSeconds()).baseUnit("seconds").register(registry);
            Gauge.builder(PREFIX + ".unknown.urgent.after", this, value -> value.escalation.urgentAfterSeconds()).baseUnit("seconds").register(registry);
            for (var kind : RecoveryInventoryCell.Kind.values()) {
                var label = kind.name().toLowerCase(Locale.ROOT);
                Gauge.builder(PREFIX + ".unknown.count", this, value -> value.count(kind)).tag("kind", label).register(registry);
                Gauge.builder(PREFIX + ".unknown.nominal.amount", this, value -> value.amount(kind))
                        .tag("kind", label).baseUnit("minor").register(registry);
                Gauge.builder(PREFIX + ".unknown.oldest.age", this, value -> value.oldestAge(kind))
                        .tag("kind", label).baseUnit("seconds").register(registry);
            }
        }
    }

    private static void requireComplete(List<RecoveryInventoryCell> cells) {
        if (cells == null || cells.size() != RecoveryInventoryCell.Kind.values().length) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var kinds = EnumSet.noneOf(RecoveryInventoryCell.Kind.class);
        for (var cell : cells) {
            if (cell == null || !kinds.add(cell.kind())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        }
    }

    @Override
    public String name() {
        return "pay.payment.recovery.inventory";
    }

    @Override
    public void execute() {
        if (!enabled || !hasRegistry || !collecting.compareAndSet(false, true)) {
            return;
        }
        try {
            var now = clock.instant();
            if (lastAttempt != null && !now.isBefore(lastAttempt) && now.isBefore(lastAttempt.plusSeconds(intervalSeconds))) {
                return;
            }
            lastAttempt = now;
            var combined = new EnumMap<RecoveryInventoryCell.Kind, RecoveryInventoryCell>(RecoveryInventoryCell.Kind.class);
            for (var kind : RecoveryInventoryCell.Kind.values()) {
                combined.put(kind, RecoveryInventoryCell.empty(kind));
            }
            try {
                for (var tenant : tenants) {
                    var cells = transactions.readSnapshot(tenant, () -> reader.read(tenant));
                    requireComplete(cells);
                    for (var cell : cells) {
                        combined.put(cell.kind(), combined.get(cell.kind()).plus(cell));
                    }
                }
                // Only a complete configured scope replaces the previous publication, never a successful partial prefix.
                snapshot.set(new Snapshot(Map.copyOf(combined), clock.instant()));
                increment(success);
            } catch (RuntimeException failed) {
                increment(failure);
                throw failed;
            }
        } finally {
            collecting.set(false);
        }
    }

    private void increment(Counter counter) {
        if (counter == null) {
            return;
        }
        try {
            counter.increment();
        } catch (RuntimeException failed) {
            LogUtil.warn(RecoveryInventoryCollector.class, "Recovery inventory metric failed type={}", failed.getClass().getSimpleName());
        }
    }

    private double count(RecoveryInventoryCell.Kind kind) {
        var current = snapshot.get();
        return current == null ? Double.NaN : current.cells.get(kind).unknownCount();
    }

    private double amount(RecoveryInventoryCell.Kind kind) {
        var current = snapshot.get();
        return current == null ? Double.NaN : current.cells.get(kind).nominalAmountMinor();
    }

    private double oldestAge(RecoveryInventoryCell.Kind kind) {
        var current = snapshot.get();
        if (current == null) {
            return Double.NaN;
        }
        var oldest = current.cells.get(kind).oldestUnknownAt();
        return oldest == null ? 0.0 : Math.max(0L, Duration.between(oldest, LocalDateTime.now(clock)).getSeconds());
    }

    private double sampleAge() {
        var current = snapshot.get();
        return current == null ? Double.NaN : Math.max(0L, Duration.between(current.sampledAt, clock.instant()).getSeconds());
    }

    private record Snapshot(Map<RecoveryInventoryCell.Kind, RecoveryInventoryCell> cells, Instant sampledAt) {
        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }
}
