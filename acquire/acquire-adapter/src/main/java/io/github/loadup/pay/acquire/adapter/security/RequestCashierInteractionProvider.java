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
package io.github.loadup.pay.acquire.adapter.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.acquire.app.port.CashierInteractionProvider;
import io.github.loadup.pay.acquire.app.security.CashierActor;
import io.github.loadup.pay.acquire.app.converter.CashierPaymentConverter;
import io.github.loadup.pay.channel.api.model.ChannelH5Input;
import io.github.loadup.pay.channel.api.model.H5SceneType;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;

/** No caller-supplied payer identity or forwarding-header chain is accepted by the hosted API. */
@Component
public class RequestCashierInteractionProvider implements CashierInteractionProvider {
    private final HttpServletRequest request;
    private final CashierPaymentConverter converter;
    public RequestCashierInteractionProvider(HttpServletRequest request, CashierPaymentConverter converter) { this.request = request; this.converter = converter; }
    @Override public PaymentInteractionDTO prepare(CashierActor actor, PaymentChoiceDTO choice) {
        if (choice.method() != PaymentMethod.H5) throw new CommonException(UNSUPPORTED_CAPABILITY);
        try { return converter.toInteraction(new ChannelH5Input(request.getRemoteAddr(), H5SceneType.WAP)); }
        catch (DomainViolation invalid) { throw new CommonException(INVALID_ARGUMENT); }
    }
}
