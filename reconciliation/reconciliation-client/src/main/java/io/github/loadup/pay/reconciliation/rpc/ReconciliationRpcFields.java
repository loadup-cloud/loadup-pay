package io.github.loadup.pay.reconciliation.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.result.ResultCode;
import io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class ReconciliationRpcFields {
    private ReconciliationRpcFields() {
    }

    public static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value);
            if (!date.toString().equals(value) || date.getYear() < 1970 || date.getYear() > 9998) {
                throw new CommonException((ResultCode)ReconciliationResultCodeEnum.INVALID_ARGUMENT);
            }
            return date;
        }
        catch (DateTimeParseException invalid) {
            throw new CommonException((ResultCode)ReconciliationResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String channel(Channel value) {
        return value == null ? null : value.name();
    }

    public static Channel channel(String value) {
        try {
            Channel channel = Channel.valueOf((String)value);
            if (channel != Channel.ALIPAY && channel != Channel.WECHAT) {
                throw new CommonException((ResultCode)ReconciliationResultCodeEnum.INVALID_ARGUMENT);
            }
            return channel;
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode)ReconciliationResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String side(ReconciliationSide value) {
        return value == null ? null : value.name();
    }

    public static ReconciliationSide side(String value) {
        try {
            return ReconciliationSide.valueOf((String)value);
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode)ReconciliationResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String state(ReconciliationState value) {
        return value == null ? null : value.name();
    }

    public static ReconciliationState state(String value) {
        try {
            return ReconciliationState.valueOf((String)value);
        }
        catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode)ReconciliationResultCodeEnum.INVALID_ARGUMENT);
        }
    }
}
