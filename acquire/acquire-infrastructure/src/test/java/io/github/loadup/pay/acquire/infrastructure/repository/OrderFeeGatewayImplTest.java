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

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.acquire.infrastructure.converter.*;
import io.github.loadup.pay.acquire.infrastructure.dataobject.*;
import io.github.loadup.pay.acquire.infrastructure.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.*;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderFeeGatewayImplTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(OrderFeeStorageConverter.class.getPackageName());
    final OrderFeeDOMapper fees = mock(OrderFeeDOMapper.class);
    final FeeAdjustmentDOMapper adjustments = mock(FeeAdjustmentDOMapper.class);
    final JsonMapper json = JsonMapper.builder().build();
    final AtomicInteger ids = new AtomicInteger();
    final OrderFeeGatewayImpl gateway = new OrderFeeGatewayImpl(fees, adjustments, context.getBean(OrderFeeStorageConverter.class),
            context.getBean(FeeAdjustmentStorageConverter.class), () -> "adjustment-" + ids.incrementAndGet(), json);
    final OrderFeePolicy policy = new OrderFeePolicy(new BigDecimal("0.003"), 0, RoundingMode.HALF_UP, 0, null, FeeRefundPolicy.PRO_RATA_CUMULATIVE);
    final AcquireOrder order = mock(AcquireOrder.class);
    final OrderFeeDO row = new OrderFeeDO();
    final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
    final UnitTransactions manager = new UnitTransactions();
    @BeforeEach void setup() {
        row.setId("fee"); row.setTenantId("tenant"); row.setOrderId("order"); row.setFeeType("ORDER_AMOUNT");
        row.setCurrency("CNY"); row.setBaseAmountMinor(10000L); row.setFeeAmountMinor(30L); row.setFeeRuleSnapshot(json.writeValueAsString(policy));
        row.setRefundBasisVersion(0L); row.setRefundBasisMinor(0L); row.setAdjustedAmountMinor(0L); row.setState("INIT"); row.setRowVersion(0L);
        when(order.id()).thenReturn("order"); when(order.amount()).thenReturn(Money.ofMinor(10000, "CNY"));
        when(order.state()).thenReturn(AcquireOrderState.PAID); when(order.paidAmountMinor()).thenReturn(10000L);
        when(fees.selectOneByQuery(any(QueryWrapper.class))).thenReturn(row);
        when(adjustments.insert(any(FeeAdjustmentDO.class))).thenReturn(1);
        when(fees.updateByQuery(any(OrderFeeDO.class), eq(true), any(QueryWrapper.class))).thenAnswer(c -> {
            OrderFeeDO patch = c.getArgument(0);
            row.setState(patch.getState()); row.setRefundBasisVersion(patch.getRefundBasisVersion());
            row.setRefundBasisMinor(patch.getRefundBasisMinor()); row.setAdjustedAmountMinor(patch.getAdjustedAmountMinor()); row.setRowVersion(patch.getRowVersion());
            if (patch.getConfirmedAt() != null) row.setConfirmedAt(patch.getConfirmedAt());
            return 1;
        });
    }
    @AfterEach void close() { context.close(); }
    void write(Runnable work) { TenantUtil.runWithTenant("tenant", () -> new TransactionTemplate(manager).executeWithoutResult(s -> work.run())); }
    void basis(long version, long amount) { write(() -> gateway.applyRefundBasis("tenant", order, policy, version, amount, "event-" + version, now)); }
    void confirm() { write(() -> gateway.confirmPaid("tenant", order, policy, now)); }
    @Test void refundBeforeConfirmationIsRetainedAndConfirmationWritesOnlyTheCumulativeDifference() {
        basis(1, 3000);
        assertEquals(3000, row.getRefundBasisMinor().longValue()); assertEquals(0, row.getAdjustedAmountMinor().longValue());
        verifyNoInteractions(adjustments);
        confirm();
        assertEquals(9, row.getAdjustedAmountMinor().longValue()); assertEquals("CONFIRMED", row.getState());
        verify(adjustments).insert(argThat(a -> a.getAdjustmentMinor() == 9 && a.getTriggerKey().equals("FEE_CONFIRMED")
                && a.getCumulativeRefundedMinor() == 3000 && a.getSourceVersion() == 1));
        basis(2, 10000);
        assertEquals(30, row.getAdjustedAmountMinor().longValue()); assertEquals(30, row.getFeeAmountMinor().longValue());
        verify(adjustments).insert(argThat(a -> a.getAdjustmentMinor() == 21 && a.getTriggerKey().equals("REFUND_SUMMARY:2")));
    }
    @Test void duplicateAndOlderSourcesCannotCreateAnotherAdjustment() {
        confirm(); basis(2, 5000); clearInvocations(fees, adjustments);
        basis(2, 5000); basis(1, 3000); confirm();
        verify(adjustments, never()).insert(any(FeeAdjustmentDO.class));
        verify(fees, never()).updateByQuery(any(OrderFeeDO.class), anyBoolean(), any(QueryWrapper.class));
        assertEquals(15, row.getAdjustedAmountMinor().longValue());
    }
    @Test void adjustmentInsertFailureStopsFeeAcknowledgementAndRollsBackTheBoundary() {
        basis(1, 3000); clearInvocations(fees);
        when(adjustments.insert(any(FeeAdjustmentDO.class))).thenReturn(0);
        assertThrows(CommonException.class, this::confirm);
        verify(fees, never()).updateByQuery(any(OrderFeeDO.class), anyBoolean(), any(QueryWrapper.class));
        assertEquals("INIT", row.getState()); assertEquals(1, manager.rollbacks);
        // This manager checks propagation only. Real ledger/CAS rollback still needs MySQL acceptance.
    }
    static class UnitTransactions extends AbstractPlatformTransactionManager {
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
