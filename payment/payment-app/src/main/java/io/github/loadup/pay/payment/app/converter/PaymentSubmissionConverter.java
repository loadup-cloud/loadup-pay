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
import io.github.loadup.pay.channel.api.model.ChannelPaymentRecovery;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.payment.domain.model.RiskConsultation;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentSubmissionConverter {
    @Mapping(target = "operationId", source = "value.payment.limitOperationId")
    @Mapping(target = "paymentId", source = "value.payment.id")
    @Mapping(target = "orderId", source = "value.payment.orderId")
    @Mapping(target = "payeeMerchantId", source = "value.payment.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "value.payment.amount.cent")
    @Mapping(target = "currency", source = "value.payment.amount.currencyValue")
    @Mapping(target = "initiatedAt", source = "value.payment.initiatedAt")
    @Mapping(target = "policyId", source = "policy.policyId")
    @Mapping(target = "policyVersion", source = "policy.policyVersion")
    @Mapping(target = "policyHash", source = "policy.policyHash")
    @Mapping(target = "requestDigest", source = "value.requestDigest")
    LimitReserveCommand toReserve(StoredPaymentAcceptance value, LimitPolicyRefDTO policy);

    @Mapping(target = "operationId", source = "limitOperationId")
    @Mapping(target = "attemptId", source = "attempt.id")
    @Mapping(target = "requestKey", source = "attempt.id", qualifiedByName = "executionKey")
    @Mapping(target = "previousAttemptEvidenceId", ignore = true)
    LimitBeginExecutionCommand toBegin(StoredPaymentAttempt value);

    @Mapping(target = "operationId", source = "permit.operationId")
    @Mapping(target = "paymentId", source = "permit.paymentId")
    @Mapping(target = "attemptId", source = "permit.attemptId")
    @Mapping(target = "executionEpoch", source = "permit.executionEpoch")
    @Mapping(target = "amountMinor", source = "value.payment.amount.cent")
    @Mapping(target = "currency", source = "value.payment.amount.currencyValue")
    @Mapping(target = "permit", source = "permit.permit")
    LimitExecutionVerifyCommand toVerify(StoredPaymentAcceptance value, LimitExecutionPermitDTO permit);

    @Mapping(target = "operationId", source = "attempt.attempt.id")
    @Mapping(target = "orderId", source = "value.payment.orderId")
    @Mapping(target = "paymentId", source = "value.payment.id")
    @Mapping(target = "attemptId", source = "attempt.attempt.id")
    @Mapping(target = "payeeMerchantId", source = "value.payment.payeeMerchantId")
    @Mapping(target = "amount", source = "value.payment.amount")
    @Mapping(target = "channel", source = "attempt.attempt.binding.channel")
    RiskConsultation toConsultation(StoredPaymentAcceptance value, StoredPaymentAttempt attempt);

    @Mapping(target = "identity", expression = "java(value.attempt().identity())")
    @Mapping(target = "amount", source = "attempt.amount")
    @Mapping(target = "method", source = "attempt.method")
    @Mapping(target = "executionEpoch", source = "attempt.executionEpoch")
    PreparedChannelPayment toPrepared(StoredPaymentAttempt value);

    @Mapping(target = "identity", expression = "java(value.attempt().identity())")
    @Mapping(target = "amount", source = "value.attempt.amount")
    @Mapping(target = "method", source = "value.attempt.method")
    @Mapping(target = "executionEpoch", source = "value.attempt.executionEpoch")
    @Mapping(target = "payableUntil", source = "payableUntil")
    ChannelPaymentRecovery toRecovery(StoredPaymentAttempt value, OffsetDateTime payableUntil);

    @Named("executionKey")
    default String executionKey(String attemptId) {
        return "attempt/" + attemptId;
    }

    default OffsetDateTime external(LocalDateTime value) {
        return BusinessTime.external(value);
    }
}
