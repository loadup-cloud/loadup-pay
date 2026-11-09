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
package io.github.loadup.acquire.adapter.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.loadup.acquire.app.converter.CashierPaymentConverter;
import io.github.loadup.acquire.app.security.CashierActor;
import io.github.loadup.acquire.client.dto.payment.H5SceneInteractionDTO;
import io.github.loadup.acquire.client.dto.payment.PaymentChoiceDTO;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.payment.channel.api.model.ChannelH5Input;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RequestCashierInteractionProviderTest {
    @Test
    void onlyServerRemoteAddressIsUsedAndJsapiCannotAcceptABrowserSuppliedIdentity() {
        var request = mock(HttpServletRequest.class);
        var converter = mock(CashierPaymentConverter.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(converter.toInteraction(any())).thenAnswer(c -> {
            var input = c.<ChannelH5Input>getArgument(0);
            return new H5SceneInteractionDTO(input.payerClientIp(), input.sceneType());
        });
        var provider = new RequestCashierInteractionProvider(request, converter);
        var actor = new CashierActor("tenant", "session", "order", LocalDateTime.of(2026, 10, 25, 12, 0));
        var result =
                (H5SceneInteractionDTO) provider.prepare(actor, new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5));
        assertEquals("203.0.113.10", result.payerClientIp());
        assertThrows(
                CommonException.class,
                () -> provider.prepare(actor, new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.JSAPI)));
        verify(request, never()).getHeader(anyString());
    }
}
