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
package io.github.loadup.pay.reconciliation.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.validation.DomainChecks;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Owner-local bounded payload carries only the original persisted request id, never a public DTO or query parameters.
 */
public final class ReconciliationVerificationMessages {
    public static final String TYPE = "pay.reconciliation.verification";
    private static final String FORMAT = "verification-request-v1:";

    private ReconciliationVerificationMessages() {
    }

    public static String encode(String id) {
        DomainChecks.id(id);
        return FORMAT + Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String payload) {
        if (payload == null || payload.length() > 512 || !payload.startsWith(FORMAT)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        try {
            var id = new String(Base64.getUrlDecoder().decode(payload.substring(FORMAT.length())), StandardCharsets.UTF_8);
            if (!encode(id).equals(payload)) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return id;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
