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
package io.github.loadup.pay.limitcenter.infrastructure.client;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.*;
import io.github.loadup.pay.limitcenter.app.port.PaymentEvidenceClient;
import io.github.loadup.pay.limitcenter.infrastructure.config.PaymentEvidenceProperties;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.*;

@Component
public class PaymentEvidenceHttpClient implements PaymentEvidenceClient {
    private final HttpTemplate http;
    private final JsonMapper mapper;
    private final PaymentEvidenceProperties properties;
    private final Clock clock;
    public PaymentEvidenceHttpClient(HttpTemplate http, JsonMapper mapper, PaymentEvidenceProperties properties,
            @Qualifier("limitcenterClock") Clock clock) {
        this.http = http; this.mapper = mapper; this.properties = properties; this.clock = clock;
    }
    @Override
    public boolean supportsTenant(String tenant) { return properties.tenantId().equals(tenant); }
    @Override
    public PaymentSettlementFactsDTO paymentFacts(String tenant, String paymentId) {
        return exchange(tenant, "payment", new IdQuery(paymentId), new TypeReference<SuccessResponse<PaymentSettlementFactsDTO>>() {});
    }

    private <T> T exchange(String tenant, String operation, Object command, TypeReference<SuccessResponse<T>> responseType) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var response = http.exchange(
                    "payment-evidence",
                    operation,
                    new HttpCall(Map.of(), Map.of(), Map.of("Authorization", "Bearer " + credential(tenant)), command));
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            SuccessResponse<T> body = mapper.readValue(response.getBody(), responseType);
            if (body == null
                    || body.result() == null
                    || body.data() == null
                    || !CommonResultCodeEnum.SUCCESS
                            .getCode()
                            .equals(body.result().getCode())
                    || !CommonResultCodeEnum.SUCCESS
                            .getStatus()
                            .equals(body.result().getStatus())) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return body.data();
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential(String tenant) {
        // Preflight routing claims only. The receiving resource server verifies signature, issuer and audience.
        try (var input = Files.newInputStream(properties.tokenFile())) {
            byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            String token = new String(bytes, StandardCharsets.US_ASCII).strip();
            String[] parts = token.split("[.]", -1);
            if (parts.length != 3 || Arrays.stream(parts).anyMatch(part -> !part.matches("[A-Za-z0-9_-]+")))
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            var claims = mapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<Map<String, Object>>() {});
            Object audience = claims.get("aud");
            boolean expectedAudience = properties.audience().equals(audience)
                    || audience instanceof Collection<?> values && values.contains(properties.audience());
            long now = clock.instant().getEpochSecond();
            if (!tenant.equals(claims.get("tenant_id"))
                    || !"SERVICE".equals(claims.get("principal_type"))
                    || !properties.serviceId().equals(claims.get("service_id"))
                    || !properties.serviceId().equals(claims.get("sub"))
                    || !expectedAudience
                    || epoch(claims.get("exp")) <= now
                    || claims.get("nbf") != null && epoch(claims.get("nbf")) > now)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return token;
        } catch (IOException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private long epoch(Object value) {
        if (!(value instanceof Number)) throw new CommonException(DEPENDENCY_UNAVAILABLE);
        return new BigDecimal(value.toString()).longValueExact();
    }
}
