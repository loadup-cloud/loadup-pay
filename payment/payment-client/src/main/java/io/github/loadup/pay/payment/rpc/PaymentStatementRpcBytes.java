package io.github.loadup.pay.payment.rpc;

import com.google.protobuf.ByteString;

public final class PaymentStatementRpcBytes {
    private PaymentStatementRpcBytes() {
    }

    public static ByteString bytes(byte[] value) {
        return value == null ? null : ByteString.copyFrom((byte[])value);
    }

    public static byte[] bytes(ByteString value) {
        return value == null ? null : value.toByteArray();
    }
}
