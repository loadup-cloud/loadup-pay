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
package io.github.loadup.pay.limitcenter.infrastructure.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.limitcenter.app.port.ExecutionPermitIssuer;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.infrastructure.config.LimitPermitProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.PERMIT_UNAVAILABLE;

/**
 * Protocol v1 uses length-framed UTF-8 fields and the framework HMAC implementation.
 */
@Component
public class HmacExecutionPermitIssuer implements ExecutionPermitIssuer {
    private final LimitPermitProperties properties;

    public HmacExecutionPermitIssuer(LimitPermitProperties properties) {
        this.properties = properties;
    }

    @Override
    public String activeKeyId() {
        return properties.activeKeyId();
    }

    @Override
    public String issue(
            String tenant,
            StoredLimitReservation stored,
            String attemptId,
            long executionEpoch,
            String requestKey,
            String permitKeyId) {
        String secret = properties.keys().get(permitKeyId);
        if (secret == null) throw new CommonException(PERMIT_UNAVAILABLE);
        var value = stored.reservation();
        var data = new StringBuilder();
        Object[] fields = {
                "loadup-pay-limit-execution-v1",
                tenant,
                stored.callerServiceId(),
                value.id(),
                value.operationId(),
                value.paymentId(),
                value.orderId(),
                value.payeeMerchantId(),
                value.amount().getCent(),
                value.amount().getCurrencyValue(),
                value.policyId(),
                value.policyVersion(),
                stored.policyHash(),
                stored.requestDigest(),
                value.initiatedAt(),
                attemptId,
                executionEpoch,
                requestKey,
                permitKeyId
        };
        for (Object field : fields) {
            String text = field.toString();
            data.append(text.getBytes(StandardCharsets.UTF_8).length)
                    .append(':')
                    .append(text);
        }
        return DigestUtils.hmacSha256(data.toString(), secret);
    }
}
