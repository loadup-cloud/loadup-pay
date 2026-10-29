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
package io.github.loadup.pay.product.rpc;

import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.domain.model.AdmissionDecision;
import io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductRpcMapperTest {
    @Test
    void timestampAndNullableRevisionRetainTheirMeaning() throws Exception {
        var time = OffsetDateTime.parse("2026-10-23T12:30:00.123456+08:00");
        var input = new OrderAdmissionDTO("admission", "order", "merchant", AdmissionDecision.ADMITTED, "ALLOWED", "digest",
                null, null, null, null, null, time, time.plusMinutes(1), null);
        var message = ProductRpcMapper.INSTANCE.toWireOrderAdmissionDTO(input);
        assertFalse(message.hasRevision());
        assertEquals(input, ProductRpcMapper.INSTANCE.fromWireOrderAdmissionDTO(WireOrderAdmissionDTO.parseFrom(message.toByteArray())));
        assertThrows(IllegalArgumentException.class, () -> ProductRpcMapper.INSTANCE.fromWireOrderAdmissionDTO(
                message.toBuilder().setDecision("UNKNOWN_SYMBOL").build()));
    }

    @Test
    void repeatedCapabilitiesAndEnumsUseMutableBuilderAdders() throws Exception {
        var capability = new io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO(
                io.github.loadup.commons.payment.Channel.ALIPAY,
                io.github.loadup.commons.payment.AccessMode.DIRECT,
                java.util.Set.of(io.github.loadup.commons.payment.PaymentMethod.H5));
        var input = new io.github.loadup.pay.product.client.dto.AcquiringTermsDTO(
                1, null, null, null, null, null, null, java.util.List.of(capability), "routing", "return");
        var wire = ProductRpcMapper.INSTANCE.toWireAcquiringTermsDTO(input);
        assertEquals(1, wire.getCapabilitiesCount());
        var parsed = io.github.loadup.pay.product.rpc.proto.WireAcquiringTermsDTO.parseFrom(wire.toByteArray());
        assertEquals(input, ProductRpcMapper.INSTANCE.fromWireAcquiringTermsDTO(parsed));
    }
}
