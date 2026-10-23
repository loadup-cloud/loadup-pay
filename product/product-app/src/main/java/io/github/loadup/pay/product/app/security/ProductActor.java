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
package io.github.loadup.pay.product.app.security;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;

/**
 * Verified delegation credentials are transport-only and never persisted with admissions.
 */
public record ProductActor(
        String tenantId,
        String principalType,
        String principalId,
        String merchantId,
        @DiagnosticHidden String customerAccessToken,
        Set<String> permissions) {
    public ProductActor {
        DomainChecks.id(tenantId);
        DomainChecks.text(principalType, 24);
        DomainChecks.text(principalId, 128);
        if (merchantId != null) DomainChecks.id(merchantId);
        permissions = Set.copyOf(permissions);
    }

    public String scopeHash() {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(JsonUtil.toJson(Arrays.asList(tenantId, principalType, principalId, merchantId))
                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
