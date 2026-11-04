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
package io.github.loadup.productcenter.domain.model;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

final class SnapshotDigest {
    private final MessageDigest digest;

    private SnapshotDigest() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new CommonException(CommonResultCodeEnum.SYS_ERROR, e);
        }
    }

    static String hash(MerchantContractRevision revision) {
        var writer = new SnapshotDigest();
        writer.token("contract-terms-v1");
        writer.token(revision.merchantId());
        writer.token(revision.scopeKey());
        writer.token(revision.contractId());
        writer.token(Integer.toString(revision.revision()));
        writer.token(revision.planCode());
        writer.token(Integer.toString(revision.planVersion()));
        writer.token(revision.signedAt().toString());
        writer.token(revision.effectiveFrom().toString());
        writer.token(
                revision.effectiveTo() == null ? null : revision.effectiveTo().toString());
        writer.token(Integer.toString(revision.items().size()));
        new TreeMap<>(revision.items()).forEach((key, item) -> {
            writer.token(key);
            writer.token(item.productCode());
            writer.token(Integer.toString(item.productVersion()));
            writer.token(item.capabilityCode());
            writer.token(item.bundleOrigin().sourceCode());
            writer.token(Integer.toString(item.bundleOrigin().sourceVersion()));
            writer.token(Integer.toString(item.configuration().values().size()));
            new TreeMap<>(item.configuration().values()).forEach((field, value) -> {
                writer.token(field);
                writer.value(value);
                ValueOrigin origin = item.configuration().origins().get(field);
                writer.token(origin.layer().name());
                writer.token(origin.sourceCode());
                writer.token(Integer.toString(origin.sourceVersion()));
            });
            writer.condition(item.usageCondition());
        });
        return "contract-terms-v1:" + HexFormat.of().formatHex(writer.digest.digest());
    }

    private void value(TypedValue value) {
        token(value.type().name());
        token(value.value());
    }

    private void token(String value) {
        if (value == null) {
            digest.update("-1:".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        digest.update(bytes);
    }

    private void condition(Condition condition) {
        switch (condition) {
            case Condition.All all -> {
                token("ALL");
                token(Integer.toString(all.children().size()));
                all.children().forEach(this::condition);
            }
            case Condition.Any any -> {
                token("ANY");
                token(Integer.toString(any.children().size()));
                any.children().forEach(this::condition);
            }
            case Condition.Not not -> {
                token("NOT");
                condition(not.child());
            }
            case Condition.Exists exists -> {
                token("EXISTS");
                token(exists.field());
            }
            case Condition.Compare compare -> {
                token("COMPARE");
                token(compare.field());
                token(compare.operator().name());
                token(compare.configurationKey());
                token(Integer.toString(compare.expected().size()));
                compare.expected().forEach(this::value);
            }
        }
    }
}
