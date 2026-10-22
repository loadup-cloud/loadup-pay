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
package io.github.loadup.pay.payment.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.domain.model.*;
import org.mapstruct.*;
import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RoutingProfileConverter {
    RoutingRule toRule(RoutingRuleDTO value);
    RoutingRuleDTO toRuleDTO(RoutingRule value);
    RoutingProfileSpec toSpec(RoutingProfileSpecDTO value);
    RoutingProfileSpecDTO toSpecDTO(RoutingProfileSpec value);
    RoutingProfileDTO toDTO(RoutingProfile value);

    @Mapping(target = "spec", source = "spec")
    RoutingProfile toStored(String id, RoutingProfileSpec spec, String contentHash, String callerServiceId,
            String requestKey, String requestDigest, LocalDateTime publishedAt);
}
