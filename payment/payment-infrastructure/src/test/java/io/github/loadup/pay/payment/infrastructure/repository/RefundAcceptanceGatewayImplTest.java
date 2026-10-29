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
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Local metadata decisions only; generated SQL, lock ownership and real rollback are covered by PostgreSQL IT sources.
 */
class RefundAcceptanceGatewayImplTest {
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundStorageConverter.class.getPackageName());
    private final RefundAcceptanceStorageConverter converter = context.getBean(RefundAcceptanceStorageConverter.class);
    private final NamedParameterJdbcTemplate receipts = mock(NamedParameterJdbcTemplate.class);
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    private final javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
    private final RefundAcceptanceGatewayImpl gateway = new RefundAcceptanceGatewayImpl(receipts, converter,
            context.getBean(RefundStorageConverter.class), Clock.systemDefaultZone());
    private final LocalDateTime at = LocalDateTime.of(2026, 10, 26, 23, 59);
    private final ChannelPaymentIdentity identity = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "revision", "a".repeat(64), "payment-request");
    private final RefundAcceptanceEvidence first = evidence(at, "receipt");

    @BeforeEach
    void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(dataSource, new Object());
        when(receipts.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
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
        when(receipts.query(contains("FROM refund_order"), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<RefundOrderDO>>any()))
                .thenReturn(List.of(child));
        when(receipts.query(contains("FROM refund_channel_receipt"), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<String>>any()))
                .thenReturn(List.of("APPLIED"));
        when(receipts.query(contains("FROM refund_acceptance_evidence"), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<RefundAcceptanceDO>>any()))
                .thenReturn(List.of());
        when(receipts.update(contains("INSERT INTO refund_acceptance_evidence"), any(SqlParameterSource.class))).thenReturn(1);
    }

    @AfterEach
    void close() {
        context.close();
        TransactionSynchronizationManager.unbindResource(dataSource);
        TransactionSynchronizationManager.clear();
    }

    @Test
    void firstAcceptanceHasOneImmutableReceiptAndDifferentTimeNeverOverwritesIt() {
        assertTrue(TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", first, 2L)));
        var stored = converter.toDO("tenant", first);
        assertEquals(first, converter.toModel(stored));
        when(receipts.query(contains("FROM refund_acceptance_evidence"), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<RefundAcceptanceDO>>any()))
                .thenReturn(List.of(stored));
        assertTrue(TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", evidence(at, "later-receipt"), 2L)));
        assertFalse(TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", evidence(at.plusMinutes(1), "later-receipt"), 2L)));
        verify(receipts, times(1)).update(contains("INSERT INTO refund_acceptance_evidence"), any(SqlParameterSource.class));
        assertEquals("receipt", TenantUtil.callWithTenant("tenant", () -> gateway.find("tenant", "refund")).orElseThrow().receiptId());
        assertEquals(at, stored.getAcceptedAt());
    }

    @Test
    void missingReceiptWrongVersionOrTimeBeforePaymentCannotCreateMetadata() {
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", first, 1L))).getResultCode());
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", evidence(at.minusDays(1), "receipt"), 2L)));
        when(receipts.query(contains("FROM refund_channel_receipt"), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<String>>any()))
                .thenReturn(List.of());
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", first, 2L)));
        verify(receipts, never()).update(contains("INSERT INTO refund_acceptance_evidence"), any(SqlParameterSource.class));
    }

    @Test
    void writableTrustedTenantIsRequiredEvenForRepeatedMetadata() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> gateway.remember("tenant", first, 2L))).getResultCode());
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other", () -> gateway.find("tenant", "refund"))).getResultCode());
        verifyNoInteractions(receipts);
    }

    private RefundAcceptanceEvidence evidence(LocalDateTime value, String receipt) {
        return new RefundAcceptanceEvidence("refund", identity, "transaction", "refund-request", Money.ofMinor(100, "CNY"), value, receipt);
    }
}
