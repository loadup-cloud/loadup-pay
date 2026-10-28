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
package io.github.loadup.pay.limitcenter.rpc;

import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.domain.model.LimitRestoreResult;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRestoreDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimitCenterRpcMapperTest {
    @Test
    void refundRestoreCommandBinaryRoundTripPreservesIdentityAndLargeCumulativeAmount() throws Exception {
        long exactValue = 9_007_199_254_741_111L;
        var command = new LimitRefundRestoreCommand("operation", "payment", "refund", exactValue, "business-event",
                1000, exactValue + 1, "CNY", "refund-evidence");

        var bytes = LimitCenterRpcMapper.INSTANCE.toWireLimitRefundRestoreCommand(command).toByteArray();
        var roundTrip = LimitCenterRpcMapper.INSTANCE.fromWireLimitRefundRestoreCommand(
                WireLimitRefundRestoreCommand.parseFrom(bytes));

        assertEquals(command, roundTrip);
        assertEquals(exactValue + 1, roundTrip.cumulativePaymentRefundedMinor());
    }

    @Test
    void refundRestoreResultBinaryRoundTripPreservesBothCumulativeAmountsAndAllIdentityFields() throws Exception {
        long original = 9_007_199_254_741_111L;
        var result = new LimitRestoreDTO("operation", "payment", "refund", original, "business-event", "refund-evidence",
                original, original + 1, original + 2, LimitRestoreResult.RESTORED, 1000, true);

        var bytes = LimitCenterRpcMapper.INSTANCE.toWireLimitRestoreDTO(result).toByteArray();
        var roundTrip = LimitCenterRpcMapper.INSTANCE.fromWireLimitRestoreDTO(WireLimitRestoreDTO.parseFrom(bytes));

        assertEquals(result, roundTrip);
        assertEquals(original, roundTrip.cumulativePaymentRefundedMinor());
        assertEquals(original + 1, roundTrip.verifiedPaymentRefundedMinor());
    }
}
