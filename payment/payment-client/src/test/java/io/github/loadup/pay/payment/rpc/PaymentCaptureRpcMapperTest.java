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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureBlocker;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCapturePage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentCaptureRpcMapperTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentCaptureRpcMapper.class.getPackageName());
    final PaymentCaptureRpcMapper mapper = context.getBean(PaymentCaptureRpcMapper.class);
    final LocalDate day = LocalDate.of(2026, 10, 26);
    final OffsetDateTime at = OffsetDateTime.parse("2026-10-27T10:00:00.123456+08:00");
    final long amount = 9_007_199_254_740_993L;

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void createPreservesDateScopeAndRejectsMissingOrUnknownFields() throws Exception {
        var command = new PaymentCaptureCreateCommand("request", Channel.WECHAT, "a".repeat(64), day, "wechat-trade-all-20260116");
        var wire = mapper.toWire(command);
        assertEquals(command, mapper.fromWire(WirePaymentCaptureCreateCommand.parseFrom(wire.toByteArray())));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearBillDate().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setBillDate("2026-02-30").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setChannel("ALIPAY").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setStatementProfile("caller-format").build()));
    }

    @Test
    void retryRequiresTheOriginalCaptureAndNativeTaskIdentities() {
        var command = new io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand("capture", "native");
        var wire = mapper.toWire(command);
        assertEquals(command, mapper.fromWire(wire));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearExpectedTaskId().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearCaptureId().build()));
    }

    @Test
    void initAndBlockedNeverAcquireImplicitZeroTotals() {
        var initial = capture(PaymentCaptureState.INIT, null, null);
        var wire = mapper.toWire(initial);
        assertFalse(wire.hasStartedAt());
        assertFalse(wire.hasPaymentCount());
        assertFalse(wire.hasContentHash());
        assertEquals(initial, mapper.fromWire(wire));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setPaymentCount(0L).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearNativeTaskId().build()));
        var blocked = mapper.toWire(capture(PaymentCaptureState.BLOCKED, null, PaymentCaptureBlocker.UNRESOLVED_COLLECTION));
        assertEquals(PaymentCaptureBlocker.UNRESOLVED_COLLECTION, mapper.fromWire(blocked).blocker());
        assertFalse(blocked.hasRefundCount());
        assertFalse(blocked.hasContentHash());
        assertThrows(CommonException.class, () -> mapper.fromWire(blocked.toBuilder().clearBlockerSubjectId().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(blocked.toBuilder().setBlocker("OTHER").build()));
    }

    @Test
    void builderAddersPreserveInt64AndPageMetadataWithStrictEofShape() throws Exception {
        var line = line(1L);
        var wire = mapper.toWire(PageDTO.of(List.of(line), 1L, 1, 100));
        assertEquals(amount, wire.getData(0).getAmountMinor());
        assertEquals(1, wire.getDataCount());
        var decoded = mapper.fromWire(WirePaymentCapturePage.parseFrom(wire.toByteArray()));
        assertEquals(1L, decoded.getPageInfo().totalCount());
        assertEquals(100L, decoded.getPageInfo().pageSize());
        assertEquals(line.sourceAt().toInstant(), decoded.getData().iterator().next().sourceAt().toInstant());
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearTotalCount().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearData().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setData(0, wire.getData(0).toBuilder().setOrdinal(2L)).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setData(0, wire.getData(0).toBuilder().clearSourceVersion()).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setData(0, wire.getData(0).toBuilder().setStatus("FAILED")).build()));
        assertTrue(mapper.fromWire(WirePaymentCapturePage.newBuilder().setTotalCount(1).setPage(2).setSize(100).build()).getData().isEmpty());
    }

    @Test
    void completeHashSurvivesWireOffsetConversionAndRejectsPartialOrChangedRows() {
        var line = line(1L);
        var hash = PaymentCaptureContentHash.compute("capture", Channel.WECHAT, "a".repeat(64), day,
                "wechat-trade-all-20260116", "Asia/Shanghai", at, at, List.of(line));
        var value = capture(PaymentCaptureState.SEALED, hash, null);
        var decoded = mapper.fromWire(mapper.toWire(value));
        var rows = mapper.fromWire(mapper.toWire(PageDTO.of(List.of(line), 1L, 1, 100))).getData();
        assertTrue(PaymentCaptureContentHash.matches(decoded, List.copyOf(rows)));
        assertFalse(PaymentCaptureContentHash.matches(decoded, List.of()));
        var changed = mapper.fromWire(mapper.toWire(line).toBuilder().setAmountMinor(amount - 1L).build());
        assertFalse(PaymentCaptureContentHash.matches(decoded, List.of(changed)));
        assertThrows(CommonException.class, () -> mapper.fromWire(mapper.toWire(value).toBuilder().clearRefundCount().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(mapper.toWire(value).toBuilder().setState("DONE").build()));
    }

    @Test
    void evidenceArchiveHashesToTheOriginalSourceDigestAndHasABoundedByteBudget() throws Exception {
        var rows = List.of(line(1));
        var hash = PaymentCaptureContentHash.compute("capture", Channel.WECHAT, "a".repeat(64), day, "wechat-trade-all-20260116", "Asia/Shanghai", at, at, rows);
        var value = capture(PaymentCaptureState.SEALED, hash, null);
        byte[] archive = PaymentCaptureContentHash.archive(value, rows, 4096);
        assertEquals(hash, HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(archive)));
        assertThrows(CommonException.class, () -> PaymentCaptureContentHash.archive(value, rows, archive.length - 1));
        assertThrows(CommonException.class, () -> PaymentCaptureContentHash.archive(value, List.of(), 4096));
    }

    @Test
    void framedNaturalKeysDoNotConfuseNullWithEmptyOrFieldBoundaries() {
        assertNotEquals(PaymentCaptureContentHash.key("REFUND", "ab", "c"), PaymentCaptureContentHash.key("REFUND", "a", "bc"));
        assertNotEquals(PaymentCaptureContentHash.key("PAYMENT", "t", null), PaymentCaptureContentHash.key("PAYMENT", "t", ""));
        assertNotEquals(PaymentCaptureContentHash.rowId("capture", 1), PaymentCaptureContentHash.rowId("other", 1));
    }

    private PaymentLedgerLineDTO line(long ordinal) {
        return new PaymentLedgerLineDTO(ordinal, PaymentLedgerKind.PAYMENT, "txn", null, "merchant-request", amount, "CNY", PaymentLedgerStatus.SUCCEEDED,
                at.minusDays(1), "payment", "receipt", 2L);
    }

    private PaymentCaptureDTO capture(PaymentCaptureState state, String hash, PaymentCaptureBlocker blocker) {
        boolean initial = state == PaymentCaptureState.INIT, sealed = state == PaymentCaptureState.SEALED;
        return new PaymentCaptureDTO("capture", Channel.WECHAT, "a".repeat(64), day, "wechat-trade-all-20260116", "Asia/Shanghai", state,
                initial ? null : at, initial ? null : at, sealed ? 1L : null, sealed ? amount : null, sealed ? 0L : null, sealed ? 0L : null,
                hash, blocker, blocker == null ? null : "subject", initial ? 0L : 1L, "native-task", "RUNNING", 1, 8);
    }
}
