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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.domain.model.*;
import org.junit.jupiter.api.Test;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDeadlineTasksTest {
    final DelayedTaskTemplate nativeTasks = mock(DelayedTaskTemplate.class);
    final OrderDeadlineTasks tasks = new OrderDeadlineTasks(nativeTasks);
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final AcquireOrder order = CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)).order();

    @Test
    void initialAndExtendedDeadlinesHaveSeparateNativeBusinessKeys() {
        transactions.writeLocal("tenant", () -> {
            tasks.register("tenant", order, 1L);
            return true;
        });
        var extended = order.extend(NOW.plusMinutes(15), order.orderDueAt(), true, 3600L, NOW);
        transactions.writeLocal("tenant", () -> {
            tasks.register("tenant", extended, 2L);
            return true;
        });
        verify(nativeTasks).register(OrderDeadlineTasks.TYPE, "order.1", order.orderDueAt());
        verify(nativeTasks).register(OrderDeadlineTasks.TYPE, "order.2", extended.orderDueAt());
        verifyNoMoreInteractions(nativeTasks);
    }

    @Test
    void registrationRequiresTheOriginalWriteTransactionAndPositiveDeadlineVersion() {
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> io.github.loadup.commons.util.TenantUtil.runWithTenant("tenant", () -> tasks.register("tenant", order, 1L))).getResultCode());
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> transactions.writeLocal("tenant", () -> { tasks.register("tenant", order, 0L); return true; })).getResultCode());
        verifyNoInteractions(nativeTasks);
    }
}
