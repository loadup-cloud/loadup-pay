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
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.domain.model.*;
import org.mapstruct.*;
import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentResultConverter {
    @Mapping(target = "identity", source = "observation.identity")
    @Mapping(target = "source", source = "observation.source")
    @Mapping(target = "receiptKey", source = "observation.receiptKey")
    @Mapping(target = "sourceDigest", source = "observation.sourceDigest")
    @Mapping(target = "observationPayload", source = "payload")
    PaymentChannelReceipt toReceipt(String id, PaymentObservation observation, String factsDigest, String payload,
            PaymentReceiptDisposition disposition, String reasonCode, LocalDateTime receivedAt);

    @Mapping(target = "identity", source = "observation.identity")
    @Mapping(target = "amount", source = "observation.amount")
    @Mapping(target = "channelTransactionNo", source = "observation.channelTransactionNo")
    @Mapping(target = "succeededAt", source = "observation.succeededAt")
    ChannelTransactionClaim toClaim(String id, String tenantId, PaymentObservation observation, String receiptId);

    @Mapping(target = "paymentId", source = "value.payment.id")
    @Mapping(target = "orderId", source = "value.payment.orderId")
    @Mapping(target = "payeeMerchantId", source = "value.payment.payeeMerchantId")
    @Mapping(target = "succeededAmount", source = "value.payment.amount")
    @Mapping(target = "refundedMinor", constant = "0L")
    @Mapping(target = "reservedMinor", constant = "0L")
    @Mapping(target = "version", constant = "0L")
    @Mapping(target = "reserve", ignore = true)
    PaymentRefundCapacity toCapacity(StoredPaymentAcceptance value, LocalDateTime succeededAt);

    @Mapping(target = "paymentId", source = "value.payment.id")
    @Mapping(target = "orderId", source = "value.payment.orderId")
    @Mapping(target = "allocationId", source = "value.payment.allocationId")
    @Mapping(target = "operationId", source = "value.payment.limitOperationId")
    @Mapping(target = "payeeMerchantId", source = "value.payment.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "value.payment.amount.cent")
    @Mapping(target = "currency", source = "value.payment.amount.currencyValue")
    @Mapping(target = "state", source = "value.payment.state")
    @Mapping(target = "paymentVersion", source = "value.payment.version")
    @Mapping(target = "evidenceId", source = "value.terminalEvidenceId")
    PaymentOutcomeEventDTO toEvent(StoredPaymentAcceptance value, boolean executionFenced);
}
