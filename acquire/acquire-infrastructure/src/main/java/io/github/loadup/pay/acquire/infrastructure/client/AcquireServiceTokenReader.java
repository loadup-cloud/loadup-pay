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

import io.github.loadup.commons.error.CommonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;

/**
 * Mounted service credentials only; local claims are preflight routing checks, never signature verification.
 */
public final class AcquireServiceTokenReader {
    private final JsonMapper mapper;
    private final Clock clock;

    public AcquireServiceTokenReader(JsonMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public String read(String tenant, String service, String audience, Path file) {
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            String token = new String(bytes, StandardCharsets.US_ASCII).strip();
            String[] parts = token.split("[.]", -1);
            if (parts.length != 3 || Arrays.stream(parts).anyMatch(part -> !part.matches("[A-Za-z0-9_-]+")))
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            var claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<Map<String, Object>>() {
            });
            Object claimAudience = claims.get("aud");
            boolean expected = audience.equals(claimAudience)
                    || claimAudience instanceof Collection<?> values && values.contains(audience);
            long now = clock.instant().getEpochSecond();
            if (!tenant.equals(claims.get("tenant_id")) || !"SERVICE".equals(claims.get("principal_type"))
                    || !service.equals(claims.get("service_id")) || !service.equals(claims.get("sub")) || !expected
                    || epoch(claims.get("exp")) <= now || claims.get("nbf") != null && epoch(claims.get("nbf")) > now)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return token;
        } catch (IOException | RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private long epoch(Object value) {
        if (!(value instanceof Number)) throw new CommonException(DEPENDENCY_UNAVAILABLE);
        return new BigDecimal(value.toString()).longValueExact();
    }
}
