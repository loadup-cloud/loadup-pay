/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.acquire.app.service;

import static io.github.loadup.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.admission;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.order;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.loadup.acquire.app.port.CashierCapabilities;
import io.github.loadup.acquire.app.port.OrderCreationCodec;
import io.github.loadup.acquire.client.dto.payment.PaymentChoiceDTO;
import io.github.loadup.acquire.client.enums.UnpaidOrderAction;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import io.github.loadup.contracts.event.acquire.AcquireOrderState;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CashierChoicesTest {
    @Test
    void hostedCatalogIsIntersectedWithTheFrozenContractAndTerminalOrdersHaveNoChoices() {
        var hosted = mock(CashierCapabilities.class);
        var codec = mock(OrderCreationCodec.class);
        var wechat = new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5);
        var alipay = new PaymentChoiceDTO(Channel.ALIPAY, PaymentMethod.H5);
        when(hosted.choices()).thenReturn(Set.of(wechat, alipay));
        when(codec.decodeAdmission(anyString(), anyString())).thenReturn(admission());
        var choices = new CashierChoices(hosted, codec);
        assertEquals(
                Set.of(wechat),
                choices.choices(order(AcquireOrderState.PENDING, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)), NOW));
        assertTrue(choices.choices(order(AcquireOrderState.PAID, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)), NOW)
                .isEmpty());
        assertTrue(choices.choices(order(AcquireOrderState.CLOSED, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)), NOW)
                .isEmpty());
    }
}
