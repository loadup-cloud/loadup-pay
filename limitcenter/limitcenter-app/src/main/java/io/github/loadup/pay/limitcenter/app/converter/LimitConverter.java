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
package io.github.loadup.pay.limitcenter.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.domain.model.LimitExecutionGrant;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface LimitConverter {
    ActivatedLimitPolicy toPolicy(LimitPolicyActivateCommand command, LocalDateTime activatedAt);

    LimitPolicyActivateCommand toCommand(ActivatedLimitPolicy policy);

    LimitRule toRule(LimitRuleDTO dto);

    LimitRuleDTO toRuleDTO(LimitRule rule);

    LimitPolicyActivationDTO toDTO(ActivatedLimitPolicy policy);

    @Mapping(target = "reservationId", source = "id")
    LimitReservationDTO toDTO(LimitReservation value);

    @Mapping(target = "operationId", source = "grant.operationId")
    @Mapping(target = "paymentId", source = "grant.paymentId")
    @Mapping(target = "attemptId", source = "grant.attemptId")
    @Mapping(target = "executionEpoch", source = "grant.executionEpoch")
    LimitExecutionPermitDTO toPermitDTO(LimitExecutionGrant grant, String permit);

    default DayOfWeek toDay(int day) {
        return DayOfWeek.of(day);
    }

    default int toDayNumber(DayOfWeek day) {
        return day.getValue();
    }

    default OffsetDateTime external(LocalDateTime time) {
        return time == null ? null : BusinessTime.external(time);
    }
}
