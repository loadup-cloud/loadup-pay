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
import io.github.loadup.pay.acquire.app.security.CashierActor;
import io.github.loadup.pay.acquire.app.security.CashierActorProvider;
import io.github.loadup.pay.acquire.app.service.CheckoutAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Collections;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAUTHORIZED;

@Component
public class RequestCashierActorProvider implements CashierActorProvider {
    private final HttpServletRequest request;
    private final CheckoutAuthenticationService authentication;

    public RequestCashierActorProvider(HttpServletRequest request, CheckoutAuthenticationService authentication) {
        this.request = request;
        this.authentication = authentication;
    }

    @Override
    public CashierActor current() {
        String authorization = single("Authorization", 128, true);
        if (!authorization.startsWith("Bearer ")) throw new CommonException(SESSION_UNAUTHORIZED);
        return authentication.authenticate(
                single("X-Checkout-Tenant", 64, true), authorization.substring(7), single("Origin", 512, false));
    }

    private String single(String name, int maximum, boolean required) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() > 1 || required && values.size() != 1) throw new CommonException(SESSION_UNAUTHORIZED);
        if (values.isEmpty()) return null;
        String value = values.getFirst();
        if (value == null
                || value.isBlank()
                || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl)) throw new CommonException(SESSION_UNAUTHORIZED);
        return value;
    }
}
