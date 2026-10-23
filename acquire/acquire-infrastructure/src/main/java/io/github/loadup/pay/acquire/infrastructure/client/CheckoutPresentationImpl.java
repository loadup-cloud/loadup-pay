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
package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.pay.acquire.app.port.CheckoutPresentation;
import io.github.loadup.pay.acquire.infrastructure.config.CheckoutProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CheckoutPresentationImpl implements CheckoutPresentation {
    private final CheckoutProperties properties;

    public CheckoutPresentationImpl(CheckoutProperties properties) {
        this.properties = properties;
    }

    @Override
    public String allowedOrigin() {
        var uri = properties.pageUrl();
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || port == 443 && "https".equals(uri.getScheme())
                || port == 80 && "http".equals(uri.getScheme());
        return uri.getScheme() + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT)
                + (defaultPort ? "" : ":" + port);
    }

    @Override
    public String url(String tenant, String order, String token) {
        return properties.pageUrl().toASCIIString() + "#tenant=" + encode(tenant) + "&order=" + encode(order)
                + "&token=" + encode(token);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
