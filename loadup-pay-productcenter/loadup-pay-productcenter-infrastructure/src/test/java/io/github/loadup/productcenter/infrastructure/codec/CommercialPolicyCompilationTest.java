/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.productcenter.infrastructure.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.loadup.productcenter.app.converter.CommercialPolicyConverter;
import io.github.loadup.productcenter.app.converter.CommercialPolicyConverterImpl;
import io.github.loadup.productcenter.app.model.ActivatedLimitPolicy;
import io.github.loadup.productcenter.app.model.CompiledAcquiringTerms;
import io.github.loadup.productcenter.app.service.AcquiringTermsCompiler;
import io.github.loadup.productcenter.app.support.ContractCodec;
import io.github.loadup.productcenter.domain.model.ConfigurationLayer;
import io.github.loadup.productcenter.domain.model.ResolvedConfiguration;
import io.github.loadup.productcenter.domain.model.TypedValue;
import io.github.loadup.productcenter.domain.model.ValueOrigin;
import io.github.loadup.productcenter.domain.policy.CommercialPolicyParser;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class CommercialPolicyCompilationTest {
    @Test
    void parsedPolicyRetainsTheFixedPublicSnapshotAndCanonicalV1Hash() throws Exception {
        try (var validation = Validation.buildDefaultValidatorFactory();
                var context = new AnnotationConfigApplicationContext(CommercialPolicyConverterImpl.class)) {
            var mapper = JsonMapper.builder().build();
            Map<String, TypedValue> values =
                    mapper.readValue(resource("configuration.json"), new TypeReference<Map<String, TypedValue>>() {});
            var origins = new LinkedHashMap<String, ValueOrigin>();
            values.keySet()
                    .forEach(key -> origins.put(key, new ValueOrigin(ConfigurationLayer.PRODUCT, "acquiring", 1)));
            var policy = new CommercialPolicyParser().parse(new ResolvedConfiguration(values, origins));
            var compiler = new AcquiringTermsCompiler(
                    validation.getValidator(), context.getBean(CommercialPolicyConverter.class));
            var terms = compiler.compile(
                    policy,
                    new ActivatedLimitPolicy(
                            "policy", 1, "a".repeat(64), "ACTIVE", LocalDateTime.of(2026, 10, 21, 0, 0)));
            var codec = new CompiledTermsCodecImpl(new ContractCodec(mapper), mapper, validation.getValidator());
            var encoded = codec.encode(new CompiledAcquiringTerms(
                    1, "contract", 1, "snapshot", terms, policy.source().origins()));
            assertEquals(mapper.readTree(resource("compiled.json")), mapper.readTree(encoded.json()));
            assertEquals(resource("compiled.sha256").strip(), encoded.hash());
        }
    }

    private String resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/commercial-policy-v1/" + name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
