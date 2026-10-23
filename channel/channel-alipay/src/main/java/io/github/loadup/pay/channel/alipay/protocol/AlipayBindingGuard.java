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
package io.github.loadup.pay.channel.alipay.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Direct adapter entry points enforce the same tenant and frozen-account boundary as the facade. */
public final class AlipayBindingGuard {
    private AlipayBindingGuard() {
    }

    public static AlipayAccountIdentity require(ChannelExecutionContext context, ChannelPaymentIdentity identity) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (context == null || identity == null) throw new CommonException(INVALID_ARGUMENT);
        if (!context.tenantId().equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var binding = context.binding();
        if (identity.channel() != Channel.ALIPAY || binding.channel() != Channel.ALIPAY
                || !(binding.identity() instanceof AlipayAccountIdentity account)
                || !binding.id().equals(identity.bindingRevisionId())
                || !binding.accountScopeHash().equals(identity.accountScopeHash()))
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        return account;
    }
}

