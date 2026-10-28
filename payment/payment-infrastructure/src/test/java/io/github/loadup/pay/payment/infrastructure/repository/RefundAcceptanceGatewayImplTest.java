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
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.RefundAcceptanceEvidence;
import io.github.loadup.pay.payment.infrastructure.converter.RefundAcceptanceStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.RefundStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAcceptanceDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundChannelReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundAcceptanceDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundChannelReceiptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Local metadata decisions only; generated SQL, lock ownership and real rollback are covered by MySQL IT sources.
 */
class RefundAcceptanceGatewayImplTest {
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundStorageConverter.class.getPackageName());
    private final RefundAcceptanceStorageConverter converter = context.getBean(RefundAcceptanceStorageConverter.class);
    private final RefundAcceptanceDOMapper accepted = mock(RefundAcceptanceDOMapper.class);
    private final RefundOrderDOMapper children = mock(RefundOrderDOMapper.class);
    private final RefundChannelReceiptDOMapper receipts = mock(RefundChannelReceiptDOMapper.class);
    private final RefundAcceptanceGatewayImpl gateway = new RefundAcceptanceGatewayImpl(accepted, children, receipts, converter, context.getBean(RefundStorageConverter.class));
    private final LocalDateTime at = LocalDateTime.of(2026, 10, 26, 23, 59);
    private final ChannelPaymentIdentity identity = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "revision", "a".repeat(64), "payment-request");
    private final RefundAcceptanceEvidence first = evidence(at, "receipt");

    @BeforeEach
    void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var child = new RefundOrderDO();
        child.setId("refund");
        child.setPaymentId("payment");
        child.setOriginalAttemptId("attempt");
        child.setChannel("WECHAT");
        child.setBindingRevisionId("revision");
        child.setAccountScopeHash("a".repeat(64));
        child.setOriginalChannelRequestNo("payment-request");
        child.setOriginalChannelTransactionNo("transaction");
        child.setChannelRefundNo("refund-request");
        child.setAmountMinor(100L);
        child.setCurrency("CNY");
        child.setSubmissionStartedAt(at.minusMinutes(1));
        child.setOriginalSucceededAt(at.minusHours(1));
        child.setRowVersion(2L);
        when(children.selectOneByQuery(any())).thenReturn(child);
        var receipt = new RefundChannelReceiptDO();
        receipt.setDisposition("APPLIED");
        when(receipts.selectOneByQuery(any())).thenReturn(receipt);
        when(accepted.insert(any(RefundAcceptanceDO.class))).thenReturn(1);
    }

    @AfterEach
    void close() {
        context.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void firstAcceptanceHasOneImmutableReceiptAndDifferentTimeNeverOverwritesIt() {
        assertTrue(TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", first, 2L)));
        var stored = converter.toDO("tenant", first);
        assertEquals(first, converter.toModel(stored));
        when(accepted.selectOneByQuery(any())).thenReturn(stored);
        assertTrue(TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", evidence(at, "later-receipt"), 2L)));
        assertFalse(TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", evidence(at.plusMinutes(1), "later-receipt"), 2L)));
        verify(accepted, times(1)).insert(any(RefundAcceptanceDO.class));
        assertEquals("receipt", TenantUtil.callWithTenant("tenant", () -> gateway.find("tenant", "refund")).orElseThrow().receiptId());
        assertEquals(at, stored.getAcceptedAt());
    }

    @Test
    void missingReceiptWrongVersionOrTimeBeforePaymentCannotCreateMetadata() {
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", first, 1L))).getResultCode());
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", evidence(at.minusDays(1), "receipt"), 2L)));
        when(receipts.selectOneByQuery(any())).thenReturn(null);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", first, 2L)));
        verify(accepted, never()).insert(any(RefundAcceptanceDO.class));
    }

    @Test
    void writableTrustedTenantIsRequiredEvenForRepeatedMetadata() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", first, 2L))).getResultCode());
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other", () -> gateway.find("tenant", "refund"))).getResultCode());
        verifyNoInteractions(accepted, children, receipts);
    }

    private RefundAcceptanceEvidence evidence(LocalDateTime value, String receipt) {
        return new RefundAcceptanceEvidence("refund", identity, "transaction", "refund-request", Money.ofMinor(100, "CNY"), value, receipt);
    }
}
