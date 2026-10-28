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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.infrastructure.converter.RefundCallbackStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRefundCapacityDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundCallbackTargetGatewayImplTest {
    private final RefundOrderDOMapper children = mock(RefundOrderDOMapper.class);
    private final PaymentRefundCapacityDOMapper capacities = mock(PaymentRefundCapacityDOMapper.class);
    private final RefundCallbackStorageConverter converter = mock(RefundCallbackStorageConverter.class);
    private final RefundCallbackTargetGatewayImpl gateway = new RefundCallbackTargetGatewayImpl(children, capacities, converter);

    @Test
    void missingTargetReturnsEmptyAndAmbiguityCannotSelectAnArbitraryRefund() {
        when(children.selectListByQuery(any())).thenReturn(List.of());
        assertTrue(TenantUtil.callWithTenant("tenant", () -> gateway.find("tenant", "original-revision", "order", "refund")).isEmpty());
        when(children.selectListByQuery(any())).thenReturn(List.of(new RefundOrderDO(), new RefundOrderDO()));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> gateway.find("tenant", "original-revision", "order", "refund")));
        verifyNoInteractions(capacities, converter);
    }

    @Test
    void unsubmittedRefundCannotBeActivatedByAnExternalNotification() {
        var row = new RefundOrderDO();
        row.setState("INIT");
        when(children.selectListByQuery(any())).thenReturn(List.of(row));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> gateway.find("tenant", "original-revision", "order", "refund")));
        verifyNoInteractions(capacities, converter);
    }

    @Test
    void originalSuccessTimestampAndCurrencyMustMatchCapacity() {
        var row = new RefundOrderDO();
        row.setState("PROCESSING");
        row.setPaymentId("payment");
        row.setCurrency("CNY");
        row.setSubmissionStartedAt(LocalDateTime.of(2026, 10, 24, 12, 0));
        row.setOriginalSucceededAt(LocalDateTime.of(2026, 10, 24, 11, 0));
        var capacity = new PaymentRefundCapacityDO();
        capacity.setCurrency("CNY");
        capacity.setSucceededAt(row.getOriginalSucceededAt().minusSeconds(1));
        when(children.selectListByQuery(any())).thenReturn(List.of(row));
        when(capacities.selectOneByQuery(any())).thenReturn(capacity);
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> gateway.find("tenant", "original-revision", "order", "refund")));
        capacity.setSucceededAt(row.getOriginalSucceededAt());
        capacity.setCurrency("USD");
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> gateway.find("tenant", "original-revision", "order", "refund")));
        verifyNoInteractions(converter);
    }

    @Test
    void tenantAndLocalTransactionAreCheckedBeforePersistence() {
        TenantUtil.runWithTenant("other", () -> assertThrows(CommonException.class,
                () -> gateway.find("tenant", "original-revision", "order", "refund")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                    () -> gateway.find("tenant", "original-revision", "order", "refund")));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(children, capacities, converter);
    }
}
