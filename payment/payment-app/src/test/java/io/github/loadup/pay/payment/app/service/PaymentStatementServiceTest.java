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
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.facade.ChannelStatementFacade;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelStatementReceipt;
import io.github.loadup.pay.channel.api.model.ChannelStatementRequest;
import io.github.loadup.pay.channel.api.model.ChannelStatementScope;
import io.github.loadup.pay.channel.api.model.VerifiedChannelStatement;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.payment.app.converter.PaymentStatementConverter;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.STATEMENT_NOT_READY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentStatementServiceTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(PaymentStatementConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final ChannelBindingGateway bindings = mock(ChannelBindingGateway.class);
    final ChannelStatementFacade channels = mock(ChannelStatementFacade.class);
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "reconciliation", Set.of("pay:payment:reconciliation:statement:read")));
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(new Tx());
    final PaymentStatementService service = new PaymentStatementService(actor::get, bindings, channels, transactions, spring.getBean(PaymentStatementConverter.class), validation.getValidator(), "reconciliation");
    final PaymentStatementQuery query = new PaymentStatementQuery("revision", Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116", 1024);
    final ChannelBindingRevision binding = new ChannelBindingRevision("revision", "merchant", Channel.WECHAT, AccessMode.DIRECT, query.accountScopeHash(), "credential", "v1", new WechatAccountIdentity("app", "mchid", null, null), Set.of(PaymentMethod.H5));
    final byte[] content = "private-statement".getBytes(StandardCharsets.UTF_8), evidence = "private-signed-grant".getBytes(StandardCharsets.UTF_8);

    static String hash(String algorithm, byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    @AfterEach
    void close() {
        spring.close();
        validation.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void onlyImmutableBindingReadUsesATransactionAndOriginalTenantIsScopedForNetwork() throws Exception {
        when(bindings.findRevision("tenant", "revision")).thenAnswer(call -> {
            assertEquals("tenant", TenantUtil.getTenantId());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(binding);
        });
        when(channels.download(any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            ChannelExecutionContext context = call.getArgument(0);
            assertSame(binding, context.binding());
            ChannelStatementRequest request = call.getArgument(1);
            assertEquals(query.maxBytes(), request.maxBytes());
            return source(query.accountScopeHash());
        });
        var result = service.download(query);
        assertArrayEquals(content, result.content());
        assertArrayEquals(evidence, result.evidence());
        assertEquals("revision", result.bindingRevisionId());
        assertEquals(query.billDate(), result.billDate());
        var copy = result.content();
        copy[0] = 0;
        assertArrayEquals(content, result.content());
        assertFalse(result.toString().contains("private-statement"));
        assertFalse(result.toString().contains("private-signed-grant"));
        verify(bindings, never()).lockActive(any(), any(), any(), any(), any());
    }

    @Test
    void servicePermissionAndNoOuterTransactionAreMandatoryBeforeAnySourceAccess() {
        actor.set(new PaymentActor("tenant", "other", actor.get().permissions()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.download(query)).getResultCode());
        actor.set(new PaymentActor("tenant", "reconciliation", Set.of()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.download(query)).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> service.download(query)).getResultCode());
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(bindings, channels);
    }

    @Test
    void bindingScopeMismatchAndUnknownRevisionNeverDownload() {
        when(bindings.findRevision("tenant", "revision")).thenReturn(Optional.empty());
        assertEquals(NOT_FOUND, assertThrows(CommonException.class, () -> service.download(query)).getResultCode());
        when(bindings.findRevision("tenant", "revision")).thenReturn(Optional.of(binding));
        var other = new PaymentStatementQuery(query.bindingRevisionId(), query.channel(), "b".repeat(64), query.billDate(), query.statementProfile(), query.maxBytes());
        assertEquals(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.download(other)).getResultCode());
        verifyNoInteractions(channels);
    }

    @Test
    void signedAbsenceIsExplicitAndOtherChannelFailuresRemainDependencyFailures() {
        when(bindings.findRevision("tenant", "revision")).thenReturn(Optional.of(binding));
        when(channels.download(any(), any())).thenThrow(new CommonException(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_FOUND));
        assertEquals(STATEMENT_NOT_FOUND, assertThrows(CommonException.class, () -> service.download(query)).getResultCode());
        when(channels.download(any(), any())).thenThrow(new CommonException(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_READY));
        assertEquals(STATEMENT_NOT_READY, assertThrows(CommonException.class, () -> service.download(query)).getResultCode());
        when(channels.download(any(), any())).thenThrow(new CommonException(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID));
        assertEquals(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> service.download(query)).getResultCode());
    }

    @Test
    void providerCannotReturnAnotherAccount() {
        when(bindings.findRevision("tenant", "revision")).thenReturn(Optional.of(binding));
        when(channels.download(any(), any())).thenAnswer(call -> source("b".repeat(64)));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.download(query)).getResultCode());
    }

    VerifiedChannelStatement source(String account) throws Exception {
        var at = OffsetDateTime.parse("2026-10-28T10:00:00.123456+08:00");
        var receipt = new ChannelStatementReceipt("wechat-tradebill-grant-v1", "c".repeat(64), hash("SHA-256", evidence), "SHA1", hash("SHA-1", content), hash("SHA-256", content), at, at, evidence);
        return new VerifiedChannelStatement("revision", new ChannelStatementScope(query.channel(), account, query.billDate(), query.statementProfile()), receipt, content);
    }

    static final class Tx extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
            assertTrue(definition.isReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, definition.getIsolationLevel());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
