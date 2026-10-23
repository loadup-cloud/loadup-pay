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
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.gateway.RoutingProfileGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingRevision;
import io.github.loadup.pay.payment.domain.model.RoutingRule;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CHANNEL_BINDING_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ROUTING_PROFILE_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * Called under parent/payment locks when freezing a new attempt's route; no caller chooses a binding id.
 */
@Component
public class PaymentRouteSelector {
    private final ChannelBindingGateway bindings;
    private final RoutingProfileGateway profiles;

    public PaymentRouteSelector(ChannelBindingGateway bindings, RoutingProfileGateway profiles) {
        this.bindings = bindings;
        this.profiles = profiles;
    }

    public ChannelBindingRevision select(String tenant, PaymentExecuteCommand accepted) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var modes = EnumSet.noneOf(AccessMode.class);
        var choice = accepted.choice();
        for (var capability : accepted.admittedTerms().terms().capabilities()) {
            if (capability.channel() == choice.channel() && capability.methods().contains(choice.method()))
                modes.add(capability.accessMode());
        }
        if (modes.isEmpty()) throw new CommonException(UNSUPPORTED_CAPABILITY);
        String ref = accepted.admittedTerms().terms().routingProfileRef();
        var profile = profiles.find(tenant, ref).orElseThrow(() -> new CommonException(ROUTING_PROFILE_UNAVAILABLE));
        if (!profile.spec().profileRef().equals(ref) || !profile.spec().payeeMerchantId().equals(accepted.payeeMerchantId()))
            throw new CommonException(ROUTING_PROFILE_UNAVAILABLE);
        var rules = profile.spec().rules().stream().filter(rule -> rule.channel() == choice.channel()
                && rule.method() == choice.method() && modes.contains(rule.accessMode())).toList();
        if (rules.isEmpty()) throw new CommonException(UNSUPPORTED_CAPABILITY);
        var routedModes = EnumSet.noneOf(AccessMode.class);
        rules.stream().map(RoutingRule::accessMode).forEach(routedModes::add);
        var candidates = bindings.lockActive(tenant, accepted.payeeMerchantId(), choice.channel(), routedModes, choice.method());
        // Profile order owns routing priority. Never escape the versioned allow-list.
        for (var rule : rules) {
            for (var candidate : candidates) {
                var selected = candidate.revision().binding();
                if (!selected.payeeMerchantId().equals(accepted.payeeMerchantId()) || selected.channel() != choice.channel()
                        || !routedModes.contains(selected.accessMode()) || !selected.capabilities().contains(choice.method()))
                    throw new CommonException(SNAPSHOT_INVALID);
                if (candidate.head().bindingCode().equals(rule.bindingCode()) && selected.accessMode() == rule.accessMode())
                    return selected;
            }
        }
        throw new CommonException(CHANNEL_BINDING_UNAVAILABLE);
    }
}
