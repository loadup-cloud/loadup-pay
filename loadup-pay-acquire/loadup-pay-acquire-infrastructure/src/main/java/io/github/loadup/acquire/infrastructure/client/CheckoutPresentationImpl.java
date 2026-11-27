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
package io.github.loadup.acquire.infrastructure.client;

import io.github.loadup.acquire.app.port.CheckoutPresentation;
import io.github.loadup.acquire.infrastructure.config.CheckoutProperties;
import io.github.loadup.components.runtime.deployment.DeploymentIdentity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CheckoutPresentationImpl implements CheckoutPresentation {
    private final CheckoutProperties properties;
    private final DeploymentIdentity deployment;

    public CheckoutPresentationImpl(CheckoutProperties properties, DeploymentIdentity deployment) {
        this.properties = properties;
        this.deployment = deployment;
    }

    @Override
    public String allowedOrigin() {
        var uri = properties.pageUrl();
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || port == 443 && "https".equals(uri.getScheme())
                || port == 80 && "http".equals(uri.getScheme());
        return uri.getScheme() + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }

    @Override
    public String url(String order, String token) {
        // The fragment carries the non-sensitive deployment code used by the cashier page for its fetch
        // header.
        return properties.pageUrl().toASCIIString()
                + "#deployment="
                + encode(deployment.code())
                + "&order="
                + encode(order)
                + "&token="
                + encode(token);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
