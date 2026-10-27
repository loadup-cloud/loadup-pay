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
package io.github.loadup.pay.acquire.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.infrastructure.model.PaymentIntentSnapshot;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.product.client.dto.*;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.OffsetDateTime;

/** Explicit Java mapping, independent of public DTO JSON names and validation annotations. */
@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentIntentSnapshotConverter {
    @Mapping(target = "format", constant = PaymentIntentSnapshot.FORMAT)
    @Mapping(target = "channel", source = "choice.channel")
    @Mapping(target = "method", source = "choice.method")
    PaymentIntentSnapshot toSnapshot(PaymentExecuteCommand value);

    @Mapping(target = "choice", source = "value")
    PaymentExecuteCommand toCommand(PaymentIntentSnapshot value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "method", source = "method")
    PaymentChoiceDTO toChoice(PaymentIntentSnapshot value);

    PaymentIntentSnapshot.Admission toSnapshot(AdmittedOrderTermsDTO value);
    AdmittedOrderTermsDTO toDTO(PaymentIntentSnapshot.Admission value);
    PaymentIntentSnapshot.Terms toSnapshot(AcquiringTermsDTO value);
    AcquiringTermsDTO toDTO(PaymentIntentSnapshot.Terms value);
    PaymentIntentSnapshot.OrderPolicy toSnapshot(OrderTermsDTO value);
    OrderTermsDTO toDTO(PaymentIntentSnapshot.OrderPolicy value);
    PaymentIntentSnapshot.PaymentPolicy toSnapshot(PaymentTermsDTO value);
    PaymentTermsDTO toDTO(PaymentIntentSnapshot.PaymentPolicy value);
    PaymentIntentSnapshot.FeePolicy toSnapshot(FeeTermsDTO value);
    FeeTermsDTO toDTO(PaymentIntentSnapshot.FeePolicy value);
    PaymentIntentSnapshot.RefundPolicy toSnapshot(RefundTermsDTO value);
    RefundTermsDTO toDTO(PaymentIntentSnapshot.RefundPolicy value);
    PaymentIntentSnapshot.NotificationPolicy toSnapshot(NotificationTermsDTO value);
    NotificationTermsDTO toDTO(PaymentIntentSnapshot.NotificationPolicy value);
    PaymentIntentSnapshot.LimitPolicy toSnapshot(LimitPolicyRefDTO value);
    LimitPolicyRefDTO toDTO(PaymentIntentSnapshot.LimitPolicy value);
    PaymentIntentSnapshot.Capability toSnapshot(ChannelCapabilityDTO value);
    ChannelCapabilityDTO toDTO(PaymentIntentSnapshot.Capability value);

    default String deadline(OffsetDateTime value) { return value == null ? null : value.toString(); }
    default OffsetDateTime deadline(String value) { return value == null ? null : OffsetDateTime.parse(value); }
}
