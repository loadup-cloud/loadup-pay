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
package io.github.loadup.pay.acquire.infrastructure.config;

import io.github.loadup.pay.acquire.app.converter.CashierPaymentConverter;
import io.github.loadup.pay.acquire.app.port.CashierCapabilities;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ConfiguredCashierCapabilities implements CashierCapabilities {
    private final Set<PaymentChoiceDTO> choices;

    public ConfiguredCashierCapabilities(CashierProperties properties, CashierPaymentConverter converter) {
        choices = properties.h5Channels().stream().map(converter::toH5Choice).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<PaymentChoiceDTO> choices() {
        return choices;
    }
}
