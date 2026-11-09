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
package io.github.loadup.payment.app.converter;

import io.github.loadup.components.payment.channel.api.model.PaymentObservation;
import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.payment.domain.model.PaymentCloseEvidence;
import java.time.LocalDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentCloseEvidenceConverter {
    PaymentCloseEvidence toEvidence(PaymentObservation observation, long executionEpoch, LocalDateTime recordedAt);

    @Mapping(target = "kind", constant = "NO_COLLECTION")
    @Mapping(target = "source", constant = "CLOSE_RESPONSE")
    @Mapping(target = "succeededAt", ignore = true)
    PaymentObservation toObservation(PaymentCloseEvidence value);
}
