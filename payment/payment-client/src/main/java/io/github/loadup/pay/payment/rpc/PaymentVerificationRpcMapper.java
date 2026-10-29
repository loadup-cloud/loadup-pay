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
package io.github.loadup.pay.payment.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.money.Money;
import io.github.loadup.components.rpc.grpc.GrpcScalarMapper;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationQuery;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

@Mapper(config = LoadUpMapStructConfig.class, uses = {PaymentVerificationRpcFields.class, GrpcScalarMapper.class}, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface PaymentVerificationRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "sourceRequestId", source = "sourceRequestId")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "accountScopeHash", source = "accountScopeHash")
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "merchantPaymentNo", source = "merchantPaymentNo")
    @Mapping(target = "channelTransactionNo", source = "channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "channelRefundNo")
    WirePaymentVerificationQuery toWire(PaymentVerificationQuery value);

    PaymentVerificationQuery fromWire(WirePaymentVerificationQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "request", source = "request")
    @Mapping(target = "disposition", source = "disposition")
    @Mapping(target = "bindingRevisionId", source = "bindingRevisionId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "attemptId", source = "attemptId")
    @Mapping(target = "refundId", source = "refundId")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    @Mapping(target = "localState", source = "localState")
    @Mapping(target = "outcome", source = "outcome")
    @Mapping(target = "observedChannelTransactionNo", source = "observedChannelTransactionNo")
    @Mapping(target = "observedRefundTransactionNo", source = "observedRefundTransactionNo")
    @Mapping(target = "succeededAt", source = "succeededAt")
    @Mapping(target = "sourceDigest", source = "sourceDigest")
    @Mapping(target = "startedAt", source = "startedAt")
    @Mapping(target = "finishedAt", source = "finishedAt")
    @Mapping(target = "acceptedAt", source = "acceptedAt")
    WirePaymentVerificationDTO toWire(PaymentVerificationDTO value);

    @Mapping(target = "amount", expression = "java(amount(value))")
    PaymentVerificationDTO fromWire(WirePaymentVerificationDTO value);

    @AfterMapping
    default void money(PaymentVerificationDTO value, @MappingTarget WirePaymentVerificationDTO.Builder target) {
        if (value != null && value.amount() != null)
            target.setAmountMinor(value.amount().getCent()).setCurrency(value.amount().getCurrencyValue());
    }

    default Money amount(WirePaymentVerificationDTO value) {
        return value.hasAmountMinor() ? Money.ofMinor(value.getAmountMinor(), value.getCurrency()) : null;
    }

    @BeforeMapping
    default void required(WirePaymentVerificationQuery value) {
        if (value == null) return;
        if (!value.hasSourceRequestId() || !value.hasChannel() || !value.hasAccountScopeHash() || !value.hasKind() || !value.hasMerchantPaymentNo() || !value.hasChannelTransactionNo())
            invalid();
        text(value.getSourceRequestId(), 64);
        text(value.getMerchantPaymentNo(), 64);
        text(value.getChannelTransactionNo(), 128);
        PaymentVerificationRpcFields.channel(value.getChannel());
        var kind = PaymentVerificationRpcFields.kind(value.getKind());
        hash(value.getAccountScopeHash());
        if ((kind == PaymentLedgerKind.REFUND) != value.hasChannelRefundNo()) invalid();
        if (value.hasChannelRefundNo()) text(value.getChannelRefundNo(), 64);
    }

    @BeforeMapping
    default void required(WirePaymentVerificationDTO value) {
        if (value == null) return;
        if (!value.hasRequest() || !value.hasDisposition() || !value.hasStartedAt() || !value.hasFinishedAt())
            invalid();
        required(value.getRequest());
        timestamp(value.getStartedAt());
        timestamp(value.getFinishedAt());
        if (value.hasSucceededAt()) timestamp(value.getSucceededAt());
        if (value.hasAcceptedAt()) timestamp(value.getAcceptedAt());
        var disposition = PaymentVerificationRpcFields.disposition(value.getDisposition());
        boolean observed = disposition == PaymentVerificationDisposition.OBSERVED;
        if (observed != value.hasBindingRevisionId() || observed != value.hasPaymentId() || observed != value.hasAttemptId()
                || observed != value.hasSourceVersion() || observed != value.hasLocalState() || observed != value.hasOutcome()
                || observed != value.hasAmountMinor() || observed != value.hasCurrency() || observed != value.hasSourceDigest()
                || (observed && "REFUND".equals(value.getRequest().getKind())) != value.hasRefundId()) invalid();
        if (!observed && (value.hasObservedChannelTransactionNo() || value.hasObservedRefundTransactionNo() || value.hasSucceededAt() || value.hasAcceptedAt()))
            invalid();
        if (observed) {
            text(value.getBindingRevisionId(), 64);
            text(value.getPaymentId(), 64);
            text(value.getAttemptId(), 64);
            text(value.getLocalState(), 32);
            hash(value.getSourceDigest());
            PaymentVerificationRpcFields.outcome(value.getOutcome());
            if (value.getSourceVersion() < 0 || value.getAmountMinor() < 1 || !"CNY".equals(value.getCurrency()))
                invalid();
        }
    }

    private void timestamp(com.google.protobuf.Timestamp value) {
        try {
            com.google.protobuf.util.Timestamps.checkValid(value);
            if (value.getNanos() % 1000 != 0) invalid();
        } catch (IllegalArgumentException malformed) {
            invalid();
        }
    }

    private void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) invalid();
    }

    private void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            invalid();
    }

    private void invalid() {
        throw new CommonException(INVALID_ARGUMENT);
    }
}
