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
package io.github.loadup.pay.payment.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.payment.domain.model.PaymentCapture;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureScope;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerLine;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentCaptureDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentCaptureLineDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentCaptureStorageConverter {
    PaymentCaptureScope toScope(PaymentCaptureDO row);

    @Mapping(target = "scope", source = "row")
    @Mapping(target = "version", source = "rowVersion")
    PaymentCapture toModel(PaymentCaptureDO row);

    @Mapping(target = "callerServiceId", source = "value.callerServiceId")
    @Mapping(target = "requestKey", source = "value.requestKey")
    @Mapping(target = "requestDigest", source = "value.requestDigest")
    @Mapping(target = "channel", source = "value.scope.channel")
    @Mapping(target = "accountScopeHash", source = "value.scope.accountScopeHash")
    @Mapping(target = "billDate", source = "value.scope.billDate")
    @Mapping(target = "statementProfile", source = "value.scope.statementProfile")
    @Mapping(target = "systemZone", source = "value.scope.systemZone")
    @Mapping(target = "state", source = "value.state")
    @Mapping(target = "startedAt", source = "value.startedAt")
    @Mapping(target = "finishedAt", source = "value.finishedAt")
    @Mapping(target = "paymentCount", source = "value.paymentCount")
    @Mapping(target = "paymentAmountMinor", source = "value.paymentAmountMinor")
    @Mapping(target = "refundCount", source = "value.refundCount")
    @Mapping(target = "refundAmountMinor", source = "value.refundAmountMinor")
    @Mapping(target = "contentHash", source = "value.contentHash")
    @Mapping(target = "blocker", source = "value.blocker")
    @Mapping(target = "blockerSubjectId", source = "value.blockerSubjectId")
    @Mapping(target = "rowVersion", source = "value.version")
    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    PaymentCaptureDO toDO(String tenant, PaymentCapture value);

    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    PaymentLedgerLine toLine(PaymentCaptureLineDO row);

    @Mapping(target = "id", expression = "java(io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash.rowId(captureId, value.ordinal()))")
    @Mapping(target = "keyHash", expression = "java(io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash.key(value.kind().name(), value.channelTransactionNo(), value.channelRefundNo()))")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "captureId", source = "captureId")
    @Mapping(target = "ordinal", source = "value.ordinal")
    @Mapping(target = "kind", source = "value.kind")
    @Mapping(target = "channelTransactionNo", source = "value.channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "value.channelRefundNo")
    @Mapping(target = "merchantPaymentNo", source = "value.merchantPaymentNo")
    @Mapping(target = "amountMinor", source = "value.amount.cent")
    @Mapping(target = "currency", source = "value.amount.currencyValue")
    @Mapping(target = "status", source = "value.status")
    @Mapping(target = "sourceAt", source = "value.sourceAt")
    @Mapping(target = "sourceId", source = "value.sourceId")
    @Mapping(target = "evidenceId", source = "value.evidenceId")
    @Mapping(target = "sourceVersion", source = "value.sourceVersion")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    PaymentCaptureLineDO toLineDO(String tenant, String captureId, PaymentLedgerLine value);
}
