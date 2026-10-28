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
package io.github.loadup.pay.reconciliation.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationInputDTO;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationBatchState;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationSide;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationInputAttachCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationRpcMapperTest {
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ReconciliationRpcMapper.class.getPackageName());
    private final ReconciliationRpcMapper mapper = context.getBean(ReconciliationRpcMapper.class);

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void originalBillDateHasNoTimeZoneConversionAndUnknownOrMissingInputsFail() {
        var command = new ReconciliationBatchCreateCommand("request", "account", LocalDate.of(2026, 10, 26));
        assertEquals(command, mapper.fromWire(mapper.toWire(command)));
        assertThrows(CommonException.class, () -> mapper.fromWire(WireReconciliationBatchCreateCommand.newBuilder().setRequestKey("key").setChannelAccountCode("account").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(mapper.toWire(command).toBuilder().setBillDate("2026-02-30").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(WireReconciliationInputAttachCommand.newBuilder().setBatchId("batch").setArtifactId("input").setSide("OTHER").build()));
    }

    @Test
    void initRetainsAbsentRunAndSummaryInsteadOfFabricatingZeroSuccess() {
        var value = new ReconciliationBatchDTO("batch", Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), "profile", ReconciliationBatchState.INIT,
                null, null, null, null, null, null, 0L);
        var wire = mapper.toWire(value);
        assertFalse(wire.hasRunId());
        assertFalse(wire.hasMatchedCount());
        assertFalse(wire.hasDifferenceCount());
        assertFalse(wire.hasResultHash());
        assertEquals(value, mapper.fromWire(wire));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setMatchedCount(0).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearVersion().build()));
    }

    @Test
    void completedBatchPreservesExplicitZeroDifferenceAndExactInt64Amounts() {
        long amount = 9_007_199_254_740_993L;
        var channel = input(ReconciliationSide.CHANNEL, amount);
        var ledger = input(ReconciliationSide.LEDGER, amount);
        var value = new ReconciliationBatchDTO("batch", Channel.ALIPAY, "a".repeat(64), LocalDate.of(2026, 10, 26), "profile", ReconciliationBatchState.MATCHED,
                channel, ledger, "run", 1L, 0L, "d".repeat(64), 4L);
        var wire = mapper.toWire(value);
        assertTrue(wire.hasDifferenceCount());
        assertEquals(0L, wire.getDifferenceCount());
        assertEquals(amount, wire.getChannelInput().getPaymentAmountMinor());
        assertEquals(value, mapper.fromWire(wire));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearDifferenceCount().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setState("UNKNOWN_STATE").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setLedgerInput(wire.getChannelInput()).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setChannel("UNIONPAY").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setChannelInput(wire.getChannelInput().toBuilder().clearPaymentCount()).build()));
    }

    private ReconciliationInputDTO input(ReconciliationSide side, long amount) {
        return new ReconciliationInputDTO(side, side.name(), "b".repeat(64), "c".repeat(64), side.name() + "-capture", "parser", 1, amount, 0, 0);
    }
}
