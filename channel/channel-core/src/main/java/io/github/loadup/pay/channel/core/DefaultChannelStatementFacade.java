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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.facade.ChannelStatementFacade;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelStatementRequest;
import io.github.loadup.pay.channel.api.model.VerifiedChannelStatement;
import io.github.loadup.pay.channel.api.spi.ChannelStatementProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_BINDING_MISMATCH;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;

/**
 * A distinct registry: no financial provider fallback, database access or claim/ACK semantics.
 */
public final class DefaultChannelStatementFacade implements ChannelStatementFacade {
    private final ChannelStrategyRegistry<ChannelStatementProvider> providers;

    public DefaultChannelStatementFacade(Collection<ChannelStatementProvider> candidates) {
        providers = new ChannelStrategyRegistry<>(candidates, ChannelStatementProvider::channel);
    }

    @Override
    public VerifiedChannelStatement download(ChannelExecutionContext context, ChannelStatementRequest request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (context == null || request == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (!context.tenantId().equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var binding = context.binding();
        var scope = request.scope();
        if (scope.channel() != binding.channel() || !scope.accountScopeHash().equals(binding.accountScopeHash())) {
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        }
        var result = providers.require(scope.channel()).download(context, request);
        if (result == null || !binding.id().equals(result.bindingRevisionId()) || !scope.equals(result.scope()) || result.size() > request.maxBytes()
                || !sha256(result.content()).equals(result.receipt().contentSha256())
                || !sha256(result.receipt().evidence()).equals(result.receipt().evidenceSha256())) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return result;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }
}
