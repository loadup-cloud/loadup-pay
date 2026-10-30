package io.github.loadup.pay.payment.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.result.ResultCode;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationOutcome;

public final class PaymentVerificationRpcFields {
    private PaymentVerificationRpcFields() {
    }

    public static String channel(Channel value) {
        return value == null ? null : value.name();
    }

    public static Channel channel(String value) {
        try {
            return Channel.valueOf((String)value);
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode)PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String kind(PaymentLedgerKind value) {
        return value == null ? null : value.name();
    }

    public static PaymentLedgerKind kind(String value) {
        try {
            return PaymentLedgerKind.valueOf((String)value);
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode)PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String disposition(PaymentVerificationDisposition value) {
        return value == null ? null : value.name();
    }

    public static PaymentVerificationDisposition disposition(String value) {
        try {
            return PaymentVerificationDisposition.valueOf((String)value);
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode)PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String outcome(PaymentVerificationOutcome value) {
        return value == null ? null : value.name();
    }

    public static PaymentVerificationOutcome outcome(String value) {
        try {
            return PaymentVerificationOutcome.valueOf((String)value);
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode)PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }
}
