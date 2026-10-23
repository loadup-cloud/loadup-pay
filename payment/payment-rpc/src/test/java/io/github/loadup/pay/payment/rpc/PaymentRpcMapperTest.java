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


import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputPrepareCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInteractionDTO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentRpcMapperTest {
    @Test
    void binaryRoundTripPreservesSensitiveOneofAndAbsentValues() throws Exception {
        var input = new PaymentInputPrepareCommand("payment", "order", "allocation", "merchant", 100, "CNY", "input",
                null, new PaymentCodeInteractionDTO("synthetic-code"));
        var bytes = PaymentRpcMapper.INSTANCE.toWirePaymentInputPrepareCommand(input).toByteArray();
        assertEquals(input, PaymentRpcMapper.INSTANCE.fromWirePaymentInputPrepareCommand(WirePaymentInputPrepareCommand.parseFrom(bytes)));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInteractionDTO(WirePaymentInteractionDTO.getDefaultInstance()));
    }


}
