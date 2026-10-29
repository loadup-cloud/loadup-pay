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
package io.github.loadup.pay.acquire.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.FeeRefundPolicy;
import io.github.loadup.pay.acquire.domain.model.OrderFeePolicy;
import io.github.loadup.pay.acquire.infrastructure.converter.FeeAdjustmentStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderFeeStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderFeeDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderFeeGatewayImplTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OrderFeeStorageConverter.class.getPackageName());
    final NamedParameterJdbcTemplate adjustments = mock(NamedParameterJdbcTemplate.class);
    final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    final javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
    final JsonMapper json = JsonMapper.builder().build();
    final AtomicInteger ids = new AtomicInteger();
    final OrderFeeGatewayImpl gateway = new OrderFeeGatewayImpl(adjustments, context.getBean(OrderFeeStorageConverter.class),
            context.getBean(FeeAdjustmentStorageConverter.class), () -> "adjustment-" + ids.incrementAndGet(), json, Clock.systemDefaultZone());
    final OrderFeePolicy policy = new OrderFeePolicy(new BigDecimal("0.003"), 0, RoundingMode.HALF_UP, 0, null, FeeRefundPolicy.PRO_RATA_CUMULATIVE);
    final AcquireOrder order = mock(AcquireOrder.class);
    final OrderFeeDO row = new OrderFeeDO();
    final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
    final UnitTransactions manager = new UnitTransactions();

    @BeforeEach
    void setup() {
        TransactionSynchronizationManager.bindResource(dataSource, new Object());
        when(adjustments.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        row.setId("fee");
        row.setTenantId("tenant");
        row.setOrderId("order");
        row.setFeeType("ORDER_AMOUNT");
        row.setCurrency("CNY");
        row.setBaseAmountMinor(10000L);
        row.setFeeAmountMinor(30L);
        row.setFeeRuleSnapshot(json.writeValueAsString(policy));
        row.setRefundBasisVersion(0L);
        row.setRefundBasisMinor(0L);
        row.setAdjustedAmountMinor(0L);
        row.setState("INIT");
        row.setRowVersion(0L);
        when(order.id()).thenReturn("order");
        when(order.amount()).thenReturn(Money.ofMinor(10000, "CNY"));
        when(order.state()).thenReturn(AcquireOrderState.PAID);
        when(order.paidAmountMinor()).thenReturn(10000L);
        when(adjustments.query(org.mockito.ArgumentMatchers.contains("FROM order_fee"), any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<OrderFeeDO>>any())).thenReturn(java.util.List.of(row));
        when(adjustments.update(org.mockito.ArgumentMatchers.contains("INSERT INTO fee_adjustment"), any(SqlParameterSource.class))).thenReturn(1);
        when(adjustments.update(org.mockito.ArgumentMatchers.contains("UPDATE order_fee"), any(SqlParameterSource.class))).thenAnswer(c -> {
            SqlParameterSource patch = c.getArgument(1);
            row.setState((String) patch.getValue("state"));
            row.setRefundBasisVersion((Long) patch.getValue("basisVersion"));
            row.setRefundBasisMinor((Long) patch.getValue("basis"));
            row.setAdjustedAmountMinor((Long) patch.getValue("adjusted"));
            row.setRowVersion((Long) patch.getValue("version"));
            if (patch.getValue("confirmed") != null) row.setConfirmedAt((LocalDateTime) patch.getValue("confirmed"));
            return 1;
        });
    }

    @AfterEach
    void close() {
        TransactionSynchronizationManager.unbindResource(dataSource);
        context.close();
    }

    void write(Runnable work) {
        TenantUtil.runWithTenant("tenant", () -> new TransactionTemplate(manager).executeWithoutResult(s -> work.run()));
    }

    void basis(long version, long amount) {
        write(() -> gateway.applyRefundBasis("tenant", order, policy, version, amount, "event-" + version, now));
    }

    void confirm() {
        write(() -> gateway.confirmPaid("tenant", order, policy, now));
    }

    @Test
    void refundBeforeConfirmationIsRetainedAndConfirmationWritesOnlyTheCumulativeDifference() {
        basis(1, 3000);
        assertEquals(3000, row.getRefundBasisMinor().longValue());
        assertEquals(0, row.getAdjustedAmountMinor().longValue());
        verify(adjustments, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO fee_adjustment"), any(SqlParameterSource.class));
        confirm();
        assertEquals(9, row.getAdjustedAmountMinor().longValue());
        assertEquals("CONFIRMED", row.getState());
        basis(2, 10000);
        assertEquals(30, row.getAdjustedAmountMinor().longValue());
        assertEquals(30, row.getFeeAmountMinor().longValue());
    }

    @Test
    void duplicateAndOlderSourcesCannotCreateAnotherAdjustment() {
        confirm();
        basis(2, 5000);
        clearInvocations(adjustments);
        basis(2, 5000);
        basis(1, 3000);
        confirm();
        verify(adjustments, never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO fee_adjustment"), any(SqlParameterSource.class));
        verify(adjustments, never()).update(org.mockito.ArgumentMatchers.contains("UPDATE order_fee"), any(SqlParameterSource.class));
        assertEquals(15, row.getAdjustedAmountMinor().longValue());
    }

    @Test
    void adjustmentInsertFailureStopsFeeAcknowledgementAndRollsBackTheBoundary() {
        basis(1, 3000);
        clearInvocations(adjustments);
        when(adjustments.update(org.mockito.ArgumentMatchers.contains("INSERT INTO fee_adjustment"), any(SqlParameterSource.class))).thenReturn(0);
        assertThrows(CommonException.class, this::confirm);
        verify(adjustments, never()).update(org.mockito.ArgumentMatchers.contains("UPDATE order_fee"), any(SqlParameterSource.class));
        assertEquals("INIT", row.getState());
        assertEquals(1, manager.rollbacks);
        // This manager checks propagation only. Real ledger/CAS rollback still needs PostgreSQL acceptance.
    }

    static class UnitTransactions extends AbstractPlatformTransactionManager {
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
