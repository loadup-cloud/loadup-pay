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
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.client.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentCaptureConverter {
    @Mapping(target = "captureId", source = "value.id")
    @Mapping(target = "channel", source = "value.scope.channel")
    @Mapping(target = "accountScopeHash", source = "value.scope.accountScopeHash")
    @Mapping(target = "billDate", source = "value.scope.billDate")
    @Mapping(target = "statementProfile", source = "value.scope.statementProfile")
    @Mapping(target = "systemZone", source = "value.scope.systemZone")
    @Mapping(target = "state", source = "value.state")
    @Mapping(target = "paymentCount", source = "value.paymentCount")
    @Mapping(target = "paymentAmountMinor", source = "value.paymentAmountMinor")
    @Mapping(target = "refundCount", source = "value.refundCount")
    @Mapping(target = "refundAmountMinor", source = "value.refundAmountMinor")
    @Mapping(target = "contentHash", source = "value.contentHash")
    @Mapping(target = "blocker", source = "value.blocker")
    @Mapping(target = "blockerSubjectId", source = "value.blockerSubjectId")
    @Mapping(target = "version", source = "value.version")
    @Mapping(target = "startedAt", expression = "java(offset(value.startedAt(), value.scope().systemZone()))")
    @Mapping(target = "finishedAt", expression = "java(offset(value.finishedAt(), value.scope().systemZone()))")
    @Mapping(target = "nativeTaskId", source = "task.id")
    @Mapping(target = "nativeStatus", source = "task.status")
    @Mapping(target = "attempts", source = "task.attempts")
    @Mapping(target = "maxAttempts", source = "task.maxAttempts")
    PaymentCaptureDTO toDTO(PaymentCapture value, io.github.loadup.components.delayedtask.DelayedTask task);

    @Mapping(target = "ordinal", source = "value.ordinal")
    @Mapping(target = "kind", source = "value.kind")
    @Mapping(target = "channelTransactionNo", source = "value.channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "value.channelRefundNo")
    @Mapping(target = "merchantPaymentNo", source = "value.merchantPaymentNo")
    @Mapping(target = "amountMinor", source = "value.amount.cent")
    @Mapping(target = "currency", source = "value.amount.currencyValue")
    @Mapping(target = "status", source = "value.status")
    @Mapping(target = "sourceAt", expression = "java(offset(value.sourceAt(), zone))")
    @Mapping(target = "sourceId", source = "value.sourceId")
    @Mapping(target = "evidenceId", source = "value.evidenceId")
    @Mapping(target = "sourceVersion", source = "value.sourceVersion")
    PaymentLedgerLineDTO toDTO(PaymentLedgerLine value, String zone);

    default java.time.OffsetDateTime offset(java.time.LocalDateTime value, String zone) {
        return value == null ? null : value.atZone(java.time.ZoneId.of(zone)).toOffsetDateTime();
    }
}
