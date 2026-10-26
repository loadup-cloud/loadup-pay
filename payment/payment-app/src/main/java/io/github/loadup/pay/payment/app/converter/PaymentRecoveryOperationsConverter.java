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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.app.model.PaymentRecoveryInspection;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.PaymentRecoveryRetryCommand;
import io.github.loadup.pay.payment.client.dto.PaymentRecoveryTaskDTO;
import io.github.loadup.pay.payment.client.dto.PaymentRecoveryRetryDTO;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryRetryReceipt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentRecoveryOperationsConverter {
    @Mapping(target = "nativeTaskId", source = "task.id")
    @Mapping(target = "nativeStatus", source = "task.status")
    @Mapping(target = "attempts", source = "task.attempts")
    @Mapping(target = "maxAttempts", source = "task.maxAttempts")
    @Mapping(target = "executeAt", source = "task.executeAt")
    @Mapping(target = "nextExecuteAt", source = "task.nextExecuteAt")
    PaymentRecoveryTaskDTO toDTO(PaymentRecoveryInspection inspection);

    PaymentRecoveryRetryDTO toDTO(PaymentRecoveryRetryReceipt receipt);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "requestKey", source = "command.requestKey")
    @Mapping(target = "callerServiceId", source = "actor.serviceId")
    @Mapping(target = "kind", source = "command.kind")
    @Mapping(target = "subjectId", source = "command.subjectId")
    @Mapping(target = "generation", source = "command.generation")
    @Mapping(target = "nativeTaskId", source = "inspection.task.id")
    @Mapping(target = "orderId", source = "inspection.orderId")
    @Mapping(target = "expectedAttempts", source = "command.expectedAttempts")
    @Mapping(target = "expectedNextExecuteAt", source = "command.expectedNextExecuteAt")
    @Mapping(target = "reason", source = "command.reason")
    PaymentRecoveryRetryReceipt toReceipt(String id, PaymentRecoveryRetryCommand command, PaymentActor actor,
            PaymentRecoveryInspection inspection, String callerScopeHash, String requestDigest, String result, LocalDateTime acceptedAt);

    default OffsetDateTime external(LocalDateTime value) { return value == null ? null : BusinessTime.external(value); }
    default LocalDateTime local(OffsetDateTime value) { return value == null ? null : BusinessTime.local(value); }
}
