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
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.model.PaymentVerificationTarget;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentVerificationConverter {
    @Mapping(target = "disposition", constant = "OBSERVED")
    @Mapping(target = "request", source = "query")
    @Mapping(target = "sourceVersion", source = "target.sourceVersion")
    @Mapping(target = "localState", source = "target.localState")
    @Mapping(target = "outcome", source = "observation.kind")
    @Mapping(target = "amount", source = "observation.amount")
    @Mapping(target = "sourceDigest", source = "observation.sourceDigest")
    @Mapping(target = "startedAt", source = "started")
    @Mapping(target = "finishedAt", source = "finished")
    @Mapping(target = "bindingRevisionId", source = "observation.identity.bindingRevisionId")
    @Mapping(target = "paymentId", source = "observation.identity.paymentId")
    @Mapping(target = "attemptId", source = "observation.identity.attemptId")
    @Mapping(target = "observedChannelTransactionNo", source = "observation.channelTransactionNo")
    @Mapping(target = "refundId", ignore = true)
    @Mapping(target = "observedRefundTransactionNo", ignore = true)
    @Mapping(target = "succeededAt", source = "observation.succeededAt", qualifiedByName = "verificationOffset")
    @Mapping(target = "acceptedAt", ignore = true)
    PaymentVerificationDTO payment(PaymentVerificationQuery query, PaymentVerificationTarget target, PaymentObservation observation, OffsetDateTime started, OffsetDateTime finished, @Context Clock clock);

    @Mapping(target = "disposition", constant = "OBSERVED")
    @Mapping(target = "request", source = "query")
    @Mapping(target = "sourceVersion", source = "target.sourceVersion")
    @Mapping(target = "localState", source = "target.localState")
    @Mapping(target = "outcome", source = "observation.kind")
    @Mapping(target = "amount", source = "observation.amount")
    @Mapping(target = "sourceDigest", source = "observation.sourceDigest")
    @Mapping(target = "startedAt", source = "started")
    @Mapping(target = "finishedAt", source = "finished")
    @Mapping(target = "bindingRevisionId", source = "observation.original.bindingRevisionId")
    @Mapping(target = "paymentId", source = "observation.original.paymentId")
    @Mapping(target = "attemptId", source = "observation.original.attemptId")
    @Mapping(target = "refundId", source = "observation.refundId")
    @Mapping(target = "observedChannelTransactionNo", source = "observation.originalChannelTransactionNo")
    @Mapping(target = "observedRefundTransactionNo", source = "observation.channelRefundTransactionNo")
    @Mapping(target = "succeededAt", source = "observation.succeededAt", qualifiedByName = "verificationOffset")
    @Mapping(target = "acceptedAt", source = "observation.acceptedAt", qualifiedByName = "verificationOffset")
    PaymentVerificationDTO refund(PaymentVerificationQuery query, PaymentVerificationTarget target, RefundObservation observation, OffsetDateTime started, OffsetDateTime finished, @Context Clock clock);

    @Mapping(target = "request", source = "query")
    @Mapping(target = "disposition", source = "disposition")
    @Mapping(target = "startedAt", source = "started")
    @Mapping(target = "finishedAt", source = "finished")
    @Mapping(target = "bindingRevisionId", ignore = true)
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "attemptId", ignore = true)
    @Mapping(target = "refundId", ignore = true)
    @Mapping(target = "sourceVersion", ignore = true)
    @Mapping(target = "localState", ignore = true)
    @Mapping(target = "outcome", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "observedChannelTransactionNo", ignore = true)
    @Mapping(target = "observedRefundTransactionNo", ignore = true)
    @Mapping(target = "succeededAt", ignore = true)
    @Mapping(target = "sourceDigest", ignore = true)
    @Mapping(target = "acceptedAt", ignore = true)
    PaymentVerificationDTO missing(PaymentVerificationQuery query, PaymentVerificationDisposition disposition, OffsetDateTime started, OffsetDateTime finished);

    @Named("verificationOffset")
    default OffsetDateTime offset(LocalDateTime value, @Context Clock clock) {
        return value == null ? null : value.atZone(clock.getZone()).toOffsetDateTime();
    }
}
