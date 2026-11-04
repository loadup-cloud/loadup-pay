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
package io.github.loadup.productcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.app.support.ContractCodec;
import io.github.loadup.productcenter.client.dto.CatalogDefinitions;
import io.github.loadup.productcenter.domain.model.ComparisonOperator;
import io.github.loadup.productcenter.domain.model.Condition;
import io.github.loadup.productcenter.domain.model.ConfigurationLayer;
import io.github.loadup.productcenter.domain.model.MerchantContractRevision;
import io.github.loadup.productcenter.domain.model.ResolvedConfiguration;
import io.github.loadup.productcenter.domain.model.ResolvedContractItem;
import io.github.loadup.productcenter.domain.model.TypedValue;
import io.github.loadup.productcenter.domain.model.ValueOrigin;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ContractCodecTest {
    private final ContractCodec codec = new ContractCodec(JsonMapper.builder().build());

    @Test
    void signedSnapshotRoundTripsPolymorphicRulesAndItsDigest() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        Condition rule = new Condition.All(List.of(
                new Condition.Not(new Condition.Exists("merchant.blocked")),
                new Condition.Compare("transaction.amount", ComparisonOperator.LE, List.of(), "limit")));
        var configuration = new ResolvedConfiguration(
                Map.of("limit", TypedValue.integer(100)),
                Map.of("limit", new ValueOrigin(ConfigurationLayer.PRODUCT, "SCAN", 1)));
        var item = new ResolvedContractItem(
                "SCAN",
                1,
                "payment.collect",
                configuration,
                rule,
                new ValueOrigin(ConfigurationLayer.BUNDLE, "BASIC", 1));
        var original = new MerchantContractRevision(
                "merchant", "store", "contract", 1, "PLAN", 1, now, now, null, Map.of("basic.scan", item));
        var restored = codec.read(codec.write(original), MerchantContractRevision.class);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.snapshotHash()).isEqualTo(original.snapshotHash());
    }

    @Test
    void requestDigestIgnoresMapAndSelectionOrderingButBindsValues() {
        var first = new LinkedHashMap<String, Object>();
        first.put("selected", new LinkedHashSet<>(List.of("a", "b")));
        first.put("values", Map.of("fee", "0.005"));
        var second = new LinkedHashMap<String, Object>();
        second.put("values", Map.of("fee", "0.005"));
        second.put("selected", new LinkedHashSet<>(List.of("b", "a")));
        assertThat(codec.digest(first)).isEqualTo(codec.digest(second));
        second.put("values", Map.of("fee", "0.006"));
        assertThat(codec.digest(first)).isNotEqualTo(codec.digest(second));
    }

    @Test
    void rejectsUnknownDefinitionFieldsAndOversizedStorage() {
        assertThatThrownBy(() -> codec.read("{\"items\":[],\"unexpected\":true}", CatalogDefinitions.Bundle.class))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.write(Map.of("value", "x".repeat(131072))))
                .isInstanceOf(CommonException.class);
    }
}
