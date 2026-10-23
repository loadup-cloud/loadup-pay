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
package io.github.loadup.pay.customer.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.components.globalunique.GlobalUniqueTemplate;
import io.github.loadup.components.globalunique.model.GlobalUniqueClaim;
import io.github.loadup.components.globalunique.model.GlobalUniqueRecord;
import io.github.loadup.pay.customer.app.security.CustomerActor;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Claims and business writes join the caller's primary-database transaction.
 */
@Component
public class CustomerCommandExecutor {
    private final GlobalUniqueTemplate unique;

    public CustomerCommandExecutor(GlobalUniqueTemplate unique) {
        this.unique = unique;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    public <T> T execute(
            CustomerActor actor,
            String operation,
            String requestKey,
            Object command,
            Class<T> responseType,
            Runnable lockPayee,
            Supplier<Prepared<T>> prepare) {
        String key = digest(JsonUtil.toJson(
                List.of(actor.tenantId(), actor.principalType().name(), actor.principalId(), requestKey)));
        String fingerprint = digest(JsonUtil.toJson(command));
        Optional<GlobalUniqueRecord> found = unique.find(operation, key);
        if (found.isPresent()) return replay(found.get(), actor, fingerprint, responseType);
        lockPayee.run();
        // A concurrent identical command can commit while this transaction waits for the payee lock.
        found = unique.find(operation, key);
        if (found.isPresent()) return replay(found.get(), actor, fingerprint, responseType);
        Prepared<T> prepared = prepare.get();
        Receipt receipt = new Receipt(fingerprint, JsonUtil.toJson(prepared.result()));
        if (!unique.claim(new GlobalUniqueClaim(operation, key, prepared.id(), JsonUtil.toJson(receipt)))) {
            return replay(unique.find(operation, key).orElseThrow(), actor, fingerprint, responseType);
        }
        prepared.persist().run();
        return prepared.result();
    }

    private <T> T replay(GlobalUniqueRecord record, CustomerActor actor, String fingerprint, Class<T> type) {
        if (!actor.tenantId().equals(record.tenantId()))
            throw new CommonException(CustomerResultCodeEnum.ACTION_FORBIDDEN);
        Receipt receipt = JsonUtil.fromJson(record.requestData(), Receipt.class);
        if (!fingerprint.equals(receipt.fingerprint()))
            throw new CommonException(CustomerResultCodeEnum.IDEMPOTENCY_CONFLICT);
        return JsonUtil.fromJson(receipt.response(), type);
    }

    public record Prepared<T>(
            String id, T result, @DiagnosticHidden Runnable persist) {
        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record Receipt(
            String fingerprint, @DiagnosticHidden String response) {
        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }
}
