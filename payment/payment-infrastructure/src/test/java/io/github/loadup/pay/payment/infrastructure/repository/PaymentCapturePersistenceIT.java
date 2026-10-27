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
package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.testcontainers.annotation.*;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.payment.app.converter.PaymentCaptureConverter;
import io.github.loadup.pay.payment.app.port.*;
import io.github.loadup.pay.payment.app.service.*;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash;
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentCaptureStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.github.loadup.pay.payment.infrastructure.repository.FinancialPersistenceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real source SQL, RR snapshots and atomic capture rollback; no financial or database substitute. */
@SpringBootTest(classes = {PaymentResultPersistenceIT.Application.class, PaymentCapturePersistenceIT.Configuration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.MYSQL, reuse = false)
class PaymentCapturePersistenceIT {
    final PaymentAcceptanceTransactions transactions;
    final PaymentAcceptanceGateway payments;
    final PaymentAttemptGateway attempts;
    final ChannelBindingManagementGateway bindings;
    final PaymentAcceptanceCodec codec;
    final ChannelBindingCodec bindingCodec;
    final PaymentResultService results;
    final PaymentCaptureReader reader;
    final FaultingCaptures captures;
    final PaymentCaptureConverter converter;
    final PaymentResultPersistenceIT.ControlledClock clock;
    final PaymentResultPersistenceIT.Faults faults;
    final JdbcTemplate jdbc;
    @Autowired PaymentCapturePersistenceIT(PaymentAcceptanceTransactions transactions, PaymentAcceptanceGateway payments,
            PaymentAttemptGateway attempts, ChannelBindingManagementGateway bindings, PaymentAcceptanceCodec codec,
            ChannelBindingCodec bindingCodec, PaymentResultService results, PaymentCaptureReader reader, FaultingCaptures captures,
            PaymentCaptureConverter converter, PaymentResultPersistenceIT.ControlledClock clock, PaymentResultPersistenceIT.Faults faults, JdbcTemplate jdbc) {
        this.transactions = transactions; this.payments = payments; this.attempts = attempts; this.bindings = bindings; this.codec = codec;
        this.bindingCodec = bindingCodec; this.results = results; this.reader = reader; this.captures = captures; this.converter = converter;
        this.clock = clock; this.faults = faults; this.jdbc = jdbc;
    }
    @BeforeEach void reset() { clock.reset(); clock.advance(Duration.ofDays(2)); faults.next.set(null); captures.failAfterSeal.set(false); }
    @Test void submittedUnknownPreventsAnEmptySuccessfulExportAndTenantScopeIsEnforced() {
        var fixture = seed(null, false); var scope = scope(fixture);
        var blocked = transactions.readSnapshot(fixture.tenant(), 10, () -> reader.read(fixture.tenant(), scope));
        assertEquals(PaymentCaptureBlocker.UNRESOLVED_COLLECTION, blocked.blocker()); assertTrue(blocked.lines().isEmpty());
        assertEquals(fixture.attempt().attempt().id(), blocked.blockerSubjectId());
        var unknown = new PaymentCaptureScope(scope.channel(), "f".repeat(64), scope.billDate(), scope.statementProfile(), scope.systemZone());
        assertEquals(PaymentCaptureBlocker.ACCOUNT_SCOPE_UNKNOWN, transactions.readSnapshot(fixture.tenant(), () -> reader.read(fixture.tenant(), unknown)).blocker());
        assertThrows(CommonException.class, () -> transactions.readSnapshot(fixture.tenant(), () -> reader.read("other", scope)));
        assertThrows(CommonException.class, () -> transactions.writeLocal(fixture.tenant(), () -> reader.read(fixture.tenant(), scope)));
        assertThrows(CommonException.class, () -> reader.read(fixture.tenant(), scope));
    }
    @Test void firstSealedRowsStayFixedWhenLaterFinancialFactsEnterTheSameAccountAndDate() {
        var fixture = seed(null, true); var original = initial(fixture); var read = read(fixture);
        assertNull(read.blocker()); assertEquals(1, read.lines().size()); seal(fixture.tenant(), original, read);
        assertEquals(1L, page(fixture.tenant(), original.id()).total());
        var later = seed(fixture.tenant(), true); assertEquals(scope(fixture), scope(later)); assertEquals(2, read(fixture).lines().size());
        var page = page(fixture.tenant(), original.id()); assertEquals(1L, page.total()); assertEquals(read.lines(), page.records());
        transactions.readSnapshot(fixture.tenant(), () -> {
            var value = captures.find(fixture.tenant(), original.id()).orElseThrow();
            assertEquals(1L, value.version()); assertEquals(10000L, value.paymentAmountMinor());
            assertThrows(CommonException.class, () -> captures.page(fixture.tenant(), value, 1, 101));
            return true;
        });
        assertEquals(0L, transactions.readSnapshot("other", () -> captures.find("other", original.id()).stream().count()));
    }
    @Test void afterAllActualCaptureSqlHasRunAFailureRollsBackRowsAndTerminalMetadataTogether() {
        var fixture = seed(null, true); var original = initial(fixture); var read = read(fixture);
        captures.failAfterSeal.set(true);
        assertThrows(IllegalStateException.class, () -> seal(fixture.tenant(), original, read));
        transactions.readSnapshot(fixture.tenant(), () -> {
            assertEquals(original, captures.find(fixture.tenant(), original.id()).orElseThrow());
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM payment_reconciliation_line WHERE tenant_id=? AND capture_id=?", Long.class, fixture.tenant(), original.id()));
            assertEquals(PaymentState.SUCCEEDED, payments.find(fixture.tenant(), fixture.command().paymentId()).orElseThrow().payment().state());
            return true;
        });
        seal(fixture.tenant(), original, read); assertEquals(1L, page(fixture.tenant(), original.id()).total());
    }
    @Test void repeatedSourceReadsInOneRrDoNotPickUpAConcurrentCommittedPayment() throws Exception {
        var fixture = seed(null, true);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            transactions.readSnapshot(fixture.tenant(), 20, () -> {
                assertEquals(1, reader.read(fixture.tenant(), scope(fixture)).lines().size());
                try { workers.submit(() -> seed(fixture.tenant(), true)).get(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError(failure); }
                assertEquals(1, reader.read(fixture.tenant(), scope(fixture)).lines().size()); return true;
            });
        }
        assertEquals(2, read(fixture).lines().size());
    }
    private Fixture seed(String tenant, boolean success) {
        var value = prepared(codec, bindingCodec);
        var fixture = tenant == null ? value : new Fixture(tenant, value.command(), value.payment(), value.attempt(), value.head(), value.revision());
        transactions.writeLocal(fixture.tenant(), () -> {
            bindings.insertHead(fixture.tenant(), fixture.head()); bindings.insertRevision(fixture.tenant(), fixture.revision());
            payments.insertParent(fixture.tenant(), new StoredPaymentGate(id("parent"), fixture.command().payeeMerchantId(), "acquire", PaymentExecutionGate.init(fixture.command().orderId(), null).open(), 0L));
            payments.insertPaymentGate(fixture.tenant(), new StoredPaymentGate(id("gate"), fixture.command().payeeMerchantId(), "acquire", PaymentExecutionGate.init(fixture.command().orderId(), fixture.command().paymentId()).open(), 0L));
            payments.insert(fixture.tenant(), fixture.payment()); attempts.insert(fixture.tenant(), fixture.attempt());
            attempts.update(fixture.tenant(), fixture.attempt().beginSubmission(1L, "d".repeat(64), RiskDecision.notConnected(), AT), 0L); return true;
        });
        if (success) results.record(fixture.tenant(), new PaymentObservation(fixture.attempt().attempt().identity(), ChannelPaymentOutcome.Kind.SUCCEEDED,
                fixture.payment().payment().amount(), id("txn"), AT, PaymentObservation.Source.QUERY_RESPONSE, "source-success", "c".repeat(64)));
        return fixture;
    }
    private PaymentCaptureScope scope(Fixture fixture) {
        var day = AT.atZone(clock.getZone()).withZoneSameInstant(ZoneOffset.ofHours(8)).toLocalDate();
        return new PaymentCaptureScope(fixture.attempt().attempt().identity().channel(), fixture.attempt().attempt().identity().accountScopeHash(), day, PaymentCaptureScope.WECHAT_ALL, clock.getZone().getId());
    }
    private PaymentCapture initial(Fixture fixture) {
        var root = PaymentCapture.init(id("capture"), "reconciliation", id("request"), "d".repeat(64), scope(fixture));
        transactions.writeLocal(fixture.tenant(), () -> { captures.insert(fixture.tenant(), root); return true; }); return root;
    }
    private PaymentCaptureRead read(Fixture fixture) { return transactions.readSnapshot(fixture.tenant(), 10, () -> reader.read(fixture.tenant(), scope(fixture))); }
    private void seal(String tenant, PaymentCapture original, PaymentCaptureRead read) {
        var scope = original.scope(); var hash = PaymentCaptureContentHash.compute(original.id(), scope.channel(), scope.accountScopeHash(), scope.billDate(), scope.statementProfile(), scope.systemZone(),
                converter.offset(read.startedAt(), scope.systemZone()), converter.offset(read.finishedAt(), scope.systemZone()), read.lines().stream().map(line -> converter.toDTO(line, scope.systemZone())).toList());
        transactions.writeLocal(tenant, () -> { captures.complete(tenant, original, original.complete(read, hash), read); return true; });
    }
    private io.github.loadup.commons.domain.PageResult<PaymentLedgerLine> page(String tenant, String id) {
        return transactions.readSnapshot(tenant, () -> captures.page(tenant, captures.find(tenant, id).orElseThrow(), 1, 100));
    }
    @TestConfiguration(proxyBeanMethods = false)
    @Import(PaymentCaptureReaderImpl.class)
    static class Configuration {
        @Bean FaultingCaptures captures(PaymentCaptureDOMapper captures, PaymentCaptureLineDOMapper lines, PaymentCaptureStorageConverter storage, PaymentCaptureConverter views) {
            return new FaultingCaptures(captures, lines, storage, views);
        }
    }
    static final class FaultingCaptures extends PaymentCaptureGatewayImpl {
        final AtomicBoolean failAfterSeal = new AtomicBoolean();
        FaultingCaptures(PaymentCaptureDOMapper captures, PaymentCaptureLineDOMapper lines, PaymentCaptureStorageConverter storage, PaymentCaptureConverter views) { super(captures, lines, storage, views); }
        @Override public void complete(String tenant, PaymentCapture original, PaymentCapture next, PaymentCaptureRead read) {
            super.complete(tenant, original, next, read);
            if (failAfterSeal.getAndSet(false)) throw new IllegalStateException("Synthetic failure after actual capture SQL");
        }
    }
}
