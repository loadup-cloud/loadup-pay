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
package io.github.loadup.pay.channel.core;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelStatementReceipt;
import io.github.loadup.pay.channel.api.model.ChannelStatementRequest;
import io.github.loadup.pay.channel.api.model.ChannelStatementScope;
import io.github.loadup.pay.channel.api.model.VerifiedChannelStatement;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.api.spi.ChannelStatementProvider;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultChannelStatementFacadeTest {
    final ChannelStatementProvider provider = mock(ChannelStatementProvider.class);
    final ChannelExecutionContext context = new ChannelExecutionContext("tenant", new ChannelBindingRevision("revision", "merchant", Channel.WECHAT, AccessMode.DIRECT,
            "a".repeat(64), "credential", "v1", new WechatAccountIdentity("app", "190000001", null, null), Set.of(PaymentMethod.H5)));
    final ChannelStatementScope scope = new ChannelStatementScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116");
    final byte[] bytes = "complete-source".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final byte[] evidence = "private-evidence".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void routesOnlyTheSelectedIndependentProviderAndRejectsChangedScopeOrSourceBytes() throws Exception {
        when(provider.channel()).thenReturn(Channel.WECHAT);
        var other = mock(ChannelStatementProvider.class);
        when(other.channel()).thenReturn(Channel.ALIPAY);
        var facade = new DefaultChannelStatementFacade(List.of(provider, other));
        var result = result(bytes, digest(bytes));
        var request = new ChannelStatementRequest(scope, 1024);
        when(provider.download(context, request)).thenReturn(result);
        assertSame(result, TenantUtil.callWithTenant("tenant", () -> facade.download(context, request)));
        verify(other, never()).download(any(), any());
        var copy = result.content();
        copy[0]++;
        assertArrayEquals(bytes, result.content());
        var proof = result.receipt().evidence();
        proof[0]++;
        assertArrayEquals(evidence, result.receipt().evidence());
        when(provider.download(context, request)).thenReturn(result(bytes, "f".repeat(64)));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> facade.download(context, request)));
        var changed = new ChannelStatementRequest(new ChannelStatementScope(scope.channel(), "b".repeat(64), scope.billDate(), scope.statementProfile()), 1024);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> facade.download(context, changed)));
    }

    @Test
    void rejectsDuplicatesMissingProvidersTransactionsAndUntrustedTenantBeforeAnyDownload() {
        when(provider.channel()).thenReturn(Channel.WECHAT);
        assertThrows(CommonException.class, () -> new DefaultChannelStatementFacade(List.of(provider, provider)));
        var request = new ChannelStatementRequest(scope, 1024);
        var facade = new DefaultChannelStatementFacade(List.of(provider));
        assertThrows(CommonException.class, () -> facade.download(context, request));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other", () -> facade.download(context, request)));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> new DefaultChannelStatementFacade(List.of()).download(context, request)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> facade.download(context, request)));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verify(provider, never()).download(any(), any());
    }

    private VerifiedChannelStatement result(byte[] content, String hash) throws Exception {
        var at = OffsetDateTime.parse("2026-10-28T12:00:00+08:00");
        return new VerifiedChannelStatement("revision", scope, new ChannelStatementReceipt("wechat-tradebill-grant-v1", "b".repeat(64), digest(evidence), "SHA1", "c".repeat(40), hash, at, at, evidence), content);
    }

    private String digest(byte[] content) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
    }
}
