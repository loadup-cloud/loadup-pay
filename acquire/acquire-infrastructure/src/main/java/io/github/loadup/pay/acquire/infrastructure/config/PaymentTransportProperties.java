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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties("loadup.modules.acquire.payment")
public record PaymentTransportProperties(
        @NotBlank @Size(max = 64) String tenantId,
        @NotBlank @Size(max = 64) String serviceId,
        @NotBlank @Size(max = 128) String audience,
        @NotNull @DiagnosticHidden Path tokenFile) {
    public PaymentTransportProperties {
        DomainChecks.id(tenantId);
        DomainChecks.id(serviceId);
        DomainChecks.text(audience, 128);
        java.util.Objects.requireNonNull(tokenFile);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
