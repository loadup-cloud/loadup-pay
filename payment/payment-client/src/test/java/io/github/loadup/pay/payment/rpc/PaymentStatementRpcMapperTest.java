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

import com.google.protobuf.ByteString;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentStatementRpcMapperTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(PaymentStatementRpcMapper.class.getPackageName());
    final PaymentStatementRpcMapper mapper = spring.getBean(PaymentStatementRpcMapper.class);
    final PaymentStatementQuery query = new PaymentStatementQuery("revision", Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116", 16777216);

    static String hash(String algorithm, byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    @AfterEach
    void close() {
        spring.close();
    }

    @Test
    void queryPreservesOriginalRevisionAndExplicitSizeAndRejectsMissingUnknownOrExpandedScope() throws Exception {
        var wire = mapper.toWire(query);
        assertEquals(query, mapper.fromWire(WirePaymentStatementQuery.parseFrom(wire.toByteArray())));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearMaxBytes().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearBindingRevisionId().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setMaxBytes(16777217).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setChannel("ALIPAY").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setStatementProfile("provider-all-accounts").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setBillDate("2026-02-30").build()));
    }

    @Test
    void binaryContentAndPrivateProofKeepExactHashesWithTimestampPresence() throws Exception {
        byte[] content = "private-statement".getBytes(StandardCharsets.UTF_8), evidence = "private-proof".getBytes(StandardCharsets.UTF_8);
        var at = OffsetDateTime.parse("2026-10-28T10:00:00.123456+08:00");
        var value = new PaymentStatementDTO(query.bindingRevisionId(), query.channel(), query.accountScopeHash(), query.billDate(), query.statementProfile(),
                "wechat-tradebill-grant-v1", "b".repeat(64), hash("SHA-256", evidence), "SHA1", hash("SHA-1", content), hash("SHA-256", content), at, at.plusSeconds(1), evidence, content);
        var wire = mapper.toWire(value);
        var roundtrip = mapper.fromWire(WirePaymentStatementDTO.parseFrom(wire.toByteArray()));
        assertArrayEquals(content, roundtrip.content());
        assertArrayEquals(evidence, roundtrip.evidence());
        assertEquals(at.toInstant(), roundtrip.receivedAt().toInstant());
        assertEquals(value.officialHash(), roundtrip.officialHash());
        assertFalse(roundtrip.toString().contains("private-statement"));
        assertFalse(roundtrip.toString().contains("private-proof"));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearEvidence().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearReceivedAt().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setContent(ByteString.copyFromUtf8("changed")).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setEvidence(ByteString.copyFromUtf8("changed")).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setOfficialHash("0".repeat(40)).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setDownloadedAt(wire.getReceivedAt().toBuilder().setNanos(123456001)).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setDownloadedAt(wire.getReceivedAt().toBuilder().setSeconds(wire.getReceivedAt().getSeconds() - 1)).build()));
        content[0] = 0;
        evidence[0] = 0;
        assertArrayEquals(roundtrip.content(), value.content());
        assertArrayEquals(roundtrip.evidence(), value.evidence());
    }
}
