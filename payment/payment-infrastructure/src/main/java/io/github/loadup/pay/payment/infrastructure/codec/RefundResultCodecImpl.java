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
package io.github.loadup.pay.payment.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.RefundResultCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class RefundResultCodecImpl implements RefundResultCodec {
    private final JsonMapper json;
    private final PaymentAcceptanceCodec hashes;

    public RefundResultCodecImpl(JsonMapper json, PaymentAcceptanceCodec hashes) {
        this.json = json;
        this.hashes = hashes;
    }

    @Override
    public String encode(RefundObservation observation) {
        var payload = json.writeValueAsString(observation);
        if (payload.getBytes(StandardCharsets.UTF_8).length > 32768) throw new CommonException(SNAPSHOT_INVALID);
        return payload;
    }

    @Override
    public String factsDigest(RefundObservation o) {
        var value = new StringBuilder("refund-facts-v2/");
        for (var field : java.util.List.of(o.refundId(), o.original().paymentId(), o.original().attemptId(), o.original().channel().name(),
                o.original().bindingRevisionId(), o.original().accountScopeHash(), o.original().channelRequestNo(), o.channelRefundNo(),
                o.originalChannelTransactionNo(), o.kind().name(), Long.toString(o.amount().getCent()), o.amount().getCurrencyValue(),
                Objects.toString(o.channelRefundTransactionNo(), ""), Objects.toString(o.succeededAt(), ""), Objects.toString(o.acceptedAt(), "")))
            value.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        return hashes.checksum(value.toString());
    }
}
