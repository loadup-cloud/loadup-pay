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
package io.github.loadup.pay.product.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.modules.contract.app.support.ContractCodec;
import io.github.loadup.pay.product.app.model.CompiledAcquiringTerms;
import io.github.loadup.pay.product.app.model.EncodedAcquiringTerms;
import io.github.loadup.pay.product.app.port.CompiledTermsCodec;
import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class CompiledTermsCodecImpl implements CompiledTermsCodec {
    private final ContractCodec codec;
    private final JsonMapper mapper;
    private final Validator validator;

    public CompiledTermsCodecImpl(ContractCodec codec, JsonMapper mapper, Validator validator) {
        this.codec = codec;
        this.mapper = mapper.rebuild()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .build();
        this.validator = validator;
    }

    @Override
    public EncodedAcquiringTerms encode(CompiledAcquiringTerms value) {
        return new EncodedAcquiringTerms(hash(value), codec.write(value));
    }

    @Override
    public CompiledAcquiringTerms decode(StoredOrderAdmission value) {
        try {
            var decoded = codec.read(value.compiledTerms(), CompiledAcquiringTerms.class);
            var reference = value.admission().contract();
            if (decoded.compilerVersion() != 1
                    || decoded.terms().schemaVersion() != 1
                    || !decoded.contractId().equals(reference.contractId())
                    || decoded.revision() != reference.revision()
                    || !decoded.snapshotHash().equals(reference.snapshotHash())
                    || !hash(decoded).equals(reference.compiledTermsHash())
                    || !validator.validate(decoded.terms()).isEmpty()) throw new CommonException(SNAPSHOT_INVALID);
            return decoded;
        } catch (RuntimeException corrupted) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private String hash(CompiledAcquiringTerms value) {
        Map<String, Object> map = mapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
        // Both capability groups and their method sets are unordered semantic collections.
        // Replace them with sorted structural values before the framework's recursive map canonicalization.
        Map<String, Object> terms =
                new TreeMap<>(mapper.convertValue(value.terms(), new TypeReference<Map<String, Object>>() {
                }));
        List<Map<String, Object>> capabilities = value.terms().capabilities().stream()
                .sorted(Comparator.comparing((io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO c) ->
                                c.channel().name())
                        .thenComparing(c -> c.accessMode().name()))
                .map(c -> Map.<String, Object>of(
                        "channel",
                        c.channel().name(),
                        "accessMode",
                        c.accessMode().name(),
                        "methods",
                        c.methods().stream().map(Enum::name).sorted().toList()))
                .toList();
        terms.put("capabilities", capabilities);
        map.put("terms", terms);
        return codec.digest(map);
    }
}
