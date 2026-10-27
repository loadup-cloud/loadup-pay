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
package io.github.loadup.pay.realtime.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.pay.acquire.client.enums.AcquireOrderState;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.app.converter.TradeProjectionConverter;
import io.github.loadup.pay.realtime.app.security.RealtimeActor;
import io.github.loadup.pay.realtime.app.security.RealtimeActorProvider;
import io.github.loadup.pay.realtime.app.service.RealtimeTransactions;
import io.github.loadup.pay.realtime.app.service.TradeProjectionService;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.domain.model.ProjectionReceipt;
import io.github.loadup.pay.realtime.infrastructure.codec.TradeProjectionCodecImpl;
import io.github.loadup.pay.realtime.infrastructure.converter.TradeStorageConverter;
import io.github.loadup.pay.realtime.infrastructure.mapper.ProjectionReceiptDOMapper;
import io.github.loadup.pay.realtime.infrastructure.mapper.TradePaymentDOMapper;
import io.github.loadup.testify.starter.scenario.TestScenario;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.SOURCE_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

/** Actual projection and receipt persistence; no transport, storage or transaction substitutes. */
@SpringBootTest(classes = ProjectionPersistenceIT.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.MYSQL, reuse = false)
class ProjectionPersistenceIT {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 27, 12, 0);
    private final TradeProjectionService service;
    private final RealtimeTransactions transactions;
    private final Actors actors;
    private final FaultingReceipts receipts;
    private final JdbcTemplate jdbc;
    private final TestScenario scenario;
    private final JsonMapper json;

    @Autowired
    ProjectionPersistenceIT(TradeProjectionService service, RealtimeTransactions transactions, Actors actors,
            FaultingReceipts receipts, JdbcTemplate jdbc, TestScenario scenario, JsonMapper json) {
        this.service = service;
        this.transactions = transactions;
        this.actors = actors;
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.scenario = scenario;
        this.json = json;
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void concurrentOriginalDeliveryHasOneProjectionAndOneOriginalReceipt(Kind kind) throws Exception {
        var fixture = new Fixture(kind);
        var results = race(() -> apply(fixture, "original", 1L, false),
                () -> apply(fixture, "original", 1L, false));
        assertNull(results.getFirst().failure());
        assertNull(results.getLast().failure());
        assertEquals(results.getFirst().ack(), results.getLast().ack());
        assertEquals("APPLIED", results.getFirst().ack().disposition());
        assertCounts(fixture, 1, 1);
        assertVersion(fixture, 1L);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void sameVersionWithDifferentFactsCannotCreateAReceiptOrOverwriteTheProjection(Kind kind) {
        var fixture = new Fixture(kind);
        apply(fixture, "original", 2L, false);
        var before = row(fixture);
        var conflict = assertThrows(CommonException.class, () -> apply(fixture, "changed", 2L, true));
        assertEquals(SOURCE_CONFLICT.getCode(), conflict.getResultCode().getCode());
        assertEquals(before, row(fixture));
        assertCounts(fixture, 1, 1);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void conflictingConcurrentFirstSnapshotsHaveOnlyOneWinner(Kind kind) throws Exception {
        var fixture = new Fixture(kind);
        var results = race(() -> apply(fixture, "first", 1L, false),
                () -> apply(fixture, "second", 1L, true));
        assertEquals(1L, results.stream().filter(value -> value.ack() != null).count());
        assertEquals(1L, results.stream().filter(value -> SOURCE_CONFLICT.getCode().equals(value.failure())).count());
        assertCounts(fixture, 1, 1);
        assertVersion(fixture, 1L);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void outOfOrderConcurrentVersionsConvergeToTheNewestFacts(Kind kind) throws Exception {
        var fixture = new Fixture(kind);
        apply(fixture, "base", 1L, false);
        var results = race(() -> apply(fixture, "older", 2L, false),
                () -> apply(fixture, "newer", 3L, true));
        for (var result : results) assertNull(result.failure());
        assertEquals("APPLIED", results.getLast().ack().disposition());
        assertEquals(3L, results.getLast().ack().projectedVersion());
        var latest = row(fixture);
        var payload = (String) latest.get("payload");
        switch (kind) {
            case ORDER -> assertEquals(200L, json.readValue(payload, OrderSnapshotEventDTO.class).paidAmountMinor());
            case PAYMENT -> assertEquals(PaymentState.UNKNOWN, json.readValue(payload, PaymentSnapshotEventDTO.class).state());
            case REFUND -> assertEquals(RefundState.UNKNOWN, json.readValue(payload, RefundSnapshotEventDTO.class).state());
            case SUMMARY -> assertEquals(200L, json.readValue(payload, OrderRefundSummaryEventDTO.class).refundReservedMinor());
        }
        var stale = apply(fixture, "late", 1L, false);
        assertEquals("STALE", stale.disposition());
        assertEquals(3L, stale.projectedVersion());
        assertEquals(latest, row(fixture));
        assertCounts(fixture, 1, 4);
        assertVersion(fixture, 3L);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void receiptFailureRollsBackProjectionAndLostAcknowledgementReplaysOriginalReceipt(Kind kind) {
        var fixture = new Fixture(kind);
        receipts.failAfterInsert.set(true);
        try {
            assertThrows(IllegalStateException.class, () -> apply(fixture, "original", 1L, false));
        } finally {
            receipts.failAfterInsert.set(false);
        }
        assertCounts(fixture, 0, 0);

        var committed = apply(fixture, "original", 1L, false);
        var before = row(fixture);
        // Discarding an ACK at the transport boundary cannot erase its committed receipt.
        assertEquals(committed, apply(fixture, "original", 1L, false));
        var duplicate = apply(fixture, "another-delivery", 1L, false);
        assertEquals("DUPLICATE", duplicate.disposition());
        var conflict = assertThrows(CommonException.class, () -> apply(fixture, "original", 1L, true));
        assertEquals(IDEMPOTENCY_CONFLICT.getCode(), conflict.getResultCode().getCode());
        assertEquals(before, row(fixture));
        assertCounts(fixture, 1, 2);
    }

    private ProjectionAckDTO apply(Fixture fixture, String delivery, long version, boolean changed) {
        return actors.call(fixture, () -> switch (fixture.kind) {
            case ORDER -> service.applyOrder(envelope(fixture, delivery, version,
                    "pay.acquire.snapshot.realtime.v1", new OrderSnapshotEventDTO(fixture.aggregate, "merchant", "merchant-order",
                    1000, "CNY", changed ? 200 : 100, 0, AcquireOrderState.PENDING,
                    BusinessTime.external(AT.minusMinutes(1)), BusinessTime.external(AT), version)));
            case PAYMENT -> service.applyPayment(envelope(fixture, delivery, version,
                    "pay.payment.snapshot.realtime.v1", new PaymentSnapshotEventDTO(fixture.aggregate, "order", "merchant",
                    1000, "CNY", changed ? PaymentState.UNKNOWN : PaymentState.PROCESSING, Channel.WECHAT, AccessMode.DIRECT,
                    BusinessTime.external(AT.minusMinutes(1)), null, version)));
            case REFUND -> service.applyRefund(envelope(fixture, delivery, version,
                    "pay.payment.refund.realtime.v1", new RefundSnapshotEventDTO(fixture.aggregate, "refund-request", "payment",
                    "order", "merchant", 1000, "CNY", changed ? RefundState.UNKNOWN : RefundState.PROCESSING,
                    BusinessTime.external(AT.minusMinutes(1)), null, version)));
            case SUMMARY -> service.applyRefundSummary(envelope(fixture, delivery, version,
                    "pay.payment.refund-summary.realtime.v1", new OrderRefundSummaryEventDTO(fixture.aggregate,
                    "merchant", "CNY", 0, changed ? 200 : 100, version)));
        });
    }

    private <T> EventEnvelope<T> envelope(Fixture fixture, String delivery, long version, String type, T data) {
        return new EventEnvelope<>(delivery, "event-" + fixture.aggregate + "-" + version, fixture.kind.producer,
                type, 1, fixture.aggregate, version, BusinessTime.external(AT), data);
    }

    private Map<String, Object> row(Fixture fixture) {
        return transactions.readSnapshot(fixture.tenant, () -> jdbc.queryForMap(
                "SELECT * FROM " + fixture.kind.table + " WHERE tenant_id = ? AND " + fixture.kind.key + " = ?",
                fixture.tenant, fixture.aggregate));
    }

    private void assertCounts(Fixture fixture, long projections, long accepted) {
        transactions.readSnapshot(fixture.tenant, () -> {
            assertEquals(projections, jdbc.queryForObject("SELECT COUNT(*) FROM " + fixture.kind.table
                    + " WHERE tenant_id = ?", Long.class, fixture.tenant).longValue());
            assertEquals(accepted, jdbc.queryForObject("SELECT COUNT(*) FROM trade_projection_receipt WHERE tenant_id = ?",
                    Long.class, fixture.tenant).longValue());
            return true;
        });
    }

    private void assertVersion(Fixture fixture, long version) {
        transactions.readSnapshot(fixture.tenant, () -> {
            scenario.assertDb(fixture.kind.table).where("tenant_id", fixture.tenant)
                    .where(fixture.kind.key, fixture.aggregate).exists().has("source_version", version);
            return true;
        });
    }

    private List<Outcome> race(Supplier<ProjectionAckDTO> first, Supplier<ProjectionAckDTO> second) throws Exception {
        var ready = new CyclicBarrier(2);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = workers.submit(() -> outcome(ready, first));
            var b = workers.submit(() -> outcome(ready, second));
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    private Outcome outcome(CyclicBarrier ready, Supplier<ProjectionAckDTO> work) throws Exception {
        ready.await(10, TimeUnit.SECONDS);
        try {
            return new Outcome(work.get(), null);
        } catch (CommonException failure) {
            return new Outcome(null, failure.getResultCode().getCode());
        }
    }

    enum Kind {
        ORDER("acquire", "order", "trade_order_projection", "order_id"),
        PAYMENT("payment", "payment", "trade_payment_projection", "payment_id"),
        REFUND("payment", "refund", "trade_refund_projection", "refund_id"),
        SUMMARY("payment", "refund-summary", "trade_refund_summary", "order_id");

        final String producer, permission, table, key;

        Kind(String producer, String permission, String table, String key) {
            this.producer = producer; this.permission = permission; this.table = table; this.key = key;
        }
    }

    private static final class Fixture {
        final Kind kind;
        final String tenant = "tenant-" + UUID.randomUUID();
        final String aggregate = "aggregate-" + UUID.randomUUID();

        Fixture(Kind kind) { this.kind = kind; }
    }

    private record Outcome(ProjectionAckDTO ack, String failure) {}

    /** Only the trusted actor adapter is supplied by the test; each racing caller owns its scope. */
    static final class Actors implements RealtimeActorProvider {
        private final ThreadLocal<RealtimeActor> current = new ThreadLocal<>();

        @Override
        public RealtimeActor current() { return current.get(); }

        <T> T call(Fixture fixture, Supplier<T> work) {
            current.set(new RealtimeActor(fixture.tenant, fixture.kind.producer, null,
                    Set.of("pay:realtime:projection:" + fixture.kind.permission)));
            try { return work.get(); } finally { current.remove(); }
        }
    }

    /** Injects failure only after the actual generated Mapper inserted the receipt. */
    static final class FaultingReceipts extends ProjectionReceiptGatewayImpl {
        final AtomicBoolean failAfterInsert = new AtomicBoolean();

        FaultingReceipts(ProjectionReceiptDOMapper mapper, TradeStorageConverter converter) { super(mapper, converter); }

        @Override
        public void insert(String tenant, ProjectionReceipt receipt) {
            super.insert(tenant, receipt);
            if (failAfterInsert.compareAndSet(true, false)) throw new IllegalStateException("Injected after actual receipt insert");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TradePaymentDOMapper.class)
    @ComponentScan(basePackageClasses = {TradeStorageConverter.class, TradeProjectionConverter.class})
    @Import({TradeProjectionGatewayImpl.class, TradeProjectionCodecImpl.class, RealtimeTransactions.class, TradeProjectionService.class})
    static class Application {
        @Bean
        Actors actors() { return new Actors(); }

        @Bean
        FaultingReceipts receipts(ProjectionReceiptDOMapper mapper, TradeStorageConverter converter) {
            return new FaultingReceipts(mapper, converter);
        }

        @Bean("realtimeClock")
        Clock realtimeClock() { return Clock.fixed(AT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()); }
    }
}
