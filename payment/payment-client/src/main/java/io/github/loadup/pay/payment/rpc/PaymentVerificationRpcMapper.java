package io.github.loadup.pay.payment.rpc;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.result.ResultCode;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
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
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(config = LoadUpMapStructConfig.class, uses = {PaymentVerificationRpcFields.class, RpcScalarMapper.class}, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface PaymentVerificationRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "sourceRequestId", source = "sourceRequestId"), @Mapping(target = "channel", source = "channel"), @Mapping(target = "accountScopeHash", source = "accountScopeHash"), @Mapping(target = "kind", source = "kind"), @Mapping(target = "merchantPaymentNo", source = "merchantPaymentNo"), @Mapping(target = "channelTransactionNo", source = "channelTransactionNo"), @Mapping(target = "channelRefundNo", source = "channelRefundNo")})
    public WirePaymentVerificationQuery toWire(PaymentVerificationQuery value);

    public PaymentVerificationQuery fromWire(WirePaymentVerificationQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "request", source = "request"), @Mapping(target = "disposition", source = "disposition"), @Mapping(target = "bindingRevisionId", source = "bindingRevisionId"), @Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "attemptId", source = "attemptId"), @Mapping(target = "refundId", source = "refundId"), @Mapping(target = "sourceVersion", source = "sourceVersion"), @Mapping(target = "localState", source = "localState"), @Mapping(target = "outcome", source = "outcome"), @Mapping(target = "observedChannelTransactionNo", source = "observedChannelTransactionNo"), @Mapping(target = "observedRefundTransactionNo", source = "observedRefundTransactionNo"), @Mapping(target = "succeededAt", source = "succeededAt"), @Mapping(target = "sourceDigest", source = "sourceDigest"), @Mapping(target = "startedAt", source = "startedAt"), @Mapping(target = "finishedAt", source = "finishedAt"), @Mapping(target = "acceptedAt", source = "acceptedAt")})
    public WirePaymentVerificationDTO toWire(PaymentVerificationDTO value);

    @Mapping(target = "amount", expression = "java(amount(value))")
    public PaymentVerificationDTO fromWire(WirePaymentVerificationDTO value);

    @AfterMapping
    default public void money(PaymentVerificationDTO value, @MappingTarget WirePaymentVerificationDTO.Builder target) {
        if (value != null && value.amount() != null) {
            target.setAmountMinor(value.amount().getCent()).setCurrency(value.amount().getCurrencyValue());
        }
    }

    default public Money amount(WirePaymentVerificationDTO value) {
        return value.hasAmountMinor() ? Money.ofMinor((long) value.getAmountMinor(), (String) value.getCurrency()) : null;
    }

    @BeforeMapping
    default public void required(WirePaymentVerificationQuery value) {
        if (value == null) {
            return;
        }
        if (!(value.hasSourceRequestId() && value.hasChannel() && value.hasAccountScopeHash() && value.hasKind() && value.hasMerchantPaymentNo() && value.hasChannelTransactionNo())) {
            this.invalid();
        }
        this.text(value.getSourceRequestId(), 64);
        this.text(value.getMerchantPaymentNo(), 64);
        this.text(value.getChannelTransactionNo(), 128);
        PaymentVerificationRpcFields.channel(value.getChannel());
        PaymentLedgerKind kind = PaymentVerificationRpcFields.kind(value.getKind());
        this.hash(value.getAccountScopeHash());
        if (kind == PaymentLedgerKind.REFUND != value.hasChannelRefundNo()) {
            this.invalid();
        }
        if (value.hasChannelRefundNo()) {
            this.text(value.getChannelRefundNo(), 64);
        }
    }

    @BeforeMapping
    default public void required(WirePaymentVerificationDTO value) {
        PaymentVerificationDisposition disposition;
        boolean observed;
        if (value == null) {
            return;
        }
        if (!(value.hasRequest() && value.hasDisposition() && value.hasStartedAt() && value.hasFinishedAt())) {
            this.invalid();
        }
        this.required(value.getRequest());
        this.timestamp(value.getStartedAt());
        this.timestamp(value.getFinishedAt());
        if (value.hasSucceededAt()) {
            this.timestamp(value.getSucceededAt());
        }
        if (value.hasAcceptedAt()) {
            this.timestamp(value.getAcceptedAt());
        }
        boolean bl = observed = (disposition = PaymentVerificationRpcFields.disposition(value.getDisposition())) == PaymentVerificationDisposition.OBSERVED;
        if (observed != value.hasBindingRevisionId() || observed != value.hasPaymentId() || observed != value.hasAttemptId() || observed != value.hasSourceVersion() || observed != value.hasLocalState() || observed != value.hasOutcome() || observed != value.hasAmountMinor() || observed != value.hasCurrency() || observed != value.hasSourceDigest() || (observed && "REFUND".equals(value.getRequest().getKind())) != value.hasRefundId()) {
            this.invalid();
        }
        if (!observed && (value.hasObservedChannelTransactionNo() || value.hasObservedRefundTransactionNo() || value.hasSucceededAt() || value.hasAcceptedAt())) {
            this.invalid();
        }
        if (observed) {
            this.text(value.getBindingRevisionId(), 64);
            this.text(value.getPaymentId(), 64);
            this.text(value.getAttemptId(), 64);
            this.text(value.getLocalState(), 32);
            this.hash(value.getSourceDigest());
            PaymentVerificationRpcFields.outcome(value.getOutcome());
            if (value.getSourceVersion() < 0L || value.getAmountMinor() < 1L || !"CNY".equals(value.getCurrency())) {
                this.invalid();
            }
        }
    }

    private void timestamp(Timestamp value) {
        try {
            Timestamps.checkValid((Timestamp) value);
            if (value.getNanos() % 1000 != 0) {
                this.invalid();
            }
        } catch (IllegalArgumentException malformed) {
            this.invalid();
        }
    }

    private void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            this.invalid();
        }
    }

    private void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            this.invalid();
        }
    }

    private void invalid() {
        throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
    }
}
