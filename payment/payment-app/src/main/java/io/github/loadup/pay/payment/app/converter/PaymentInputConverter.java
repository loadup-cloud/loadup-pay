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
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.domain.model.EncryptedPaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentInputConverter {
    @Mapping(target = "id", source = "inputRef")
    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(command.amountMinor(), command.currency()))")
    @Mapping(target = "channel", source = "command.choice.channel")
    @Mapping(target = "method", source = "command.choice.method")
    @Mapping(target = "state", constant = "INIT")
    @Mapping(target = "boundAttemptId", ignore = true)
    @Mapping(target = "rowVersion", constant = "0L")
    PaymentInput toInput(PaymentInputPrepareCommand command, String inputRef, String callerServiceId,
                         String fingerprintKeyId, String inputDigest, EncryptedPaymentInput encrypted,
                         LocalDateTime preparedAt, LocalDateTime expiresAt);

    @Mapping(target = "inputRef", source = "id")
    PaymentInputDTO toDTO(PaymentInput value);

    default OffsetDateTime external(LocalDateTime value) {
        return BusinessTime.external(value);
    }
}
