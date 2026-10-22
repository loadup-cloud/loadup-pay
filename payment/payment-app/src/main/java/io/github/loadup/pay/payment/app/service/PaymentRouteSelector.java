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
import io.github.loadup.pay.common.AccessMode;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingRevision;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.EnumSet;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** Called under parent/payment locks when freezing a new attempt's route; no caller chooses a binding id. */
@Component
public class PaymentRouteSelector {
    private final ChannelBindingGateway bindings;
    public PaymentRouteSelector(ChannelBindingGateway bindings) { this.bindings = bindings; }
    public ChannelBindingRevision select(String tenant, PaymentExecuteCommand accepted) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var modes = EnumSet.noneOf(AccessMode.class);
        var choice = accepted.choice();
        for (var capability : accepted.admittedTerms().terms().capabilities()) {
            if (capability.channel() == choice.channel() && capability.methods().contains(choice.method()))
                modes.add(capability.accessMode());
        }
        if (modes.isEmpty()) throw new CommonException(UNSUPPORTED_CAPABILITY);
        var candidates = bindings.lockActive(tenant, accepted.payeeMerchantId(), choice.channel(), modes, choice.method());
        if (candidates.isEmpty()) throw new CommonException(CHANNEL_BINDING_UNAVAILABLE);
        var selected = candidates.getFirst().revision().binding();
        if (!selected.payeeMerchantId().equals(accepted.payeeMerchantId()) || selected.channel() != choice.channel()
                || !modes.contains(selected.accessMode()) || !selected.capabilities().contains(choice.method()))
            throw new CommonException(SNAPSHOT_INVALID);
        return selected;
    }
}
