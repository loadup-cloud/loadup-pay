/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.components.payment.channel.core.codec;

import static io.github.loadup.components.payment.channel.api.error.ChannelResultCodeEnum.ACTION_SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.payment.channel.api.codec.ChannelActionTypeCodec;
import io.github.loadup.components.payment.channel.api.model.AlipayJsapiChannelAction;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistryChannelActionCodecTest {
    private static final AlipayJsapiChannelAction ACTION =
            new AlipayJsapiChannelAction("trade-1", OffsetDateTime.parse("2030-01-01T12:00:00+08:00"));

    @Test
    void routesEncodeDecodeKindAndChecksumThroughTheRegisteredTypeCodec() {
        var codec = new RegistryChannelActionCodec(List.of(new TestCodec("ALIPAY_JSAPI", ACTION)));
        var payload = codec.encode(ACTION);
        assertEquals("ALIPAY_JSAPI", codec.kind(ACTION));
        assertEquals(payload, codec.encode(ACTION));
        assertEquals(codec.checksum(payload), codec.checksum(payload));
        assertEquals(ACTION, codec.decode("ALIPAY_JSAPI", payload, codec.checksum(payload)));
    }

    @Test
    void rejectsMissingInvalidDuplicateAndConflictingRegistrations() {
        assertThrows(CommonException.class, () -> new RegistryChannelActionCodec(null));
        assertThrows(
                CommonException.class,
                () -> new RegistryChannelActionCodec(Arrays.asList((ChannelActionTypeCodec<?>) null)));
        assertThrows(
                CommonException.class,
                () -> new RegistryChannelActionCodec(List.of(new TestCodec("bad kind", ACTION))));
        assertThrows(CommonException.class, () -> new RegistryChannelActionCodec(List.of(new TestCodec(null, ACTION))));
        assertThrows(
                CommonException.class,
                () -> new RegistryChannelActionCodec(
                        List.of(new TestCodec("ALIPAY_JSAPI", ACTION), new TestCodec("ALIPAY_H5", ACTION))));
        assertThrows(
                CommonException.class,
                () -> new RegistryChannelActionCodec(
                        List.of(new TestCodec("ALIPAY_JSAPI", ACTION), new TestCodec("ALIPAY_JSAPI", ACTION))));
    }

    @Test
    void rejectsUnknownTypeAndUnknownKind() {
        var codec = new RegistryChannelActionCodec(List.of(new TestCodec("ALIPAY_JSAPI", ACTION)));
        assertThrows(CommonException.class, () -> codec.kind(null));
        var other = new AlipayJsapiChannelAction("trade-2", ACTION.expiresAt());
        assertEquals("ALIPAY_JSAPI", codec.kind(other));
        var empty = new RegistryChannelActionCodec(List.of());
        assertThrows(CommonException.class, () -> empty.kind(ACTION));
        assertThrows(CommonException.class, () -> empty.encode(ACTION));
        assertThrows(CommonException.class, () -> empty.decode("ALIPAY_JSAPI", "payload", empty.checksum("payload")));
    }

    @Test
    void rejectsPayloadSizeChecksumKindAndCodecResultViolations() {
        var codec = new RegistryChannelActionCodec(List.of(new TestCodec("ALIPAY_JSAPI", ACTION)));
        var oversized = " ".repeat(RegistryChannelActionCodec.MAX_PAYLOAD_LENGTH + 1);
        assertThrows(CommonException.class, () -> codec.decode("ALIPAY_JSAPI", oversized, codec.checksum(oversized)));
        assertThrows(CommonException.class, () -> codec.decode("ALIPAY_JSAPI", "payload", "bad"));
        assertThrows(CommonException.class, () -> codec.decode("ALIPAY_JSAPI", "payload", null));
        assertThrows(CommonException.class, () -> codec.decode(null, "payload", codec.checksum("payload")));
        assertThrows(CommonException.class, () -> codec.decode("ALIPAY_H5", "payload", codec.checksum("payload")));
        var nullCodec = new RegistryChannelActionCodec(List.of(new NullDecodingCodec()));
        assertThrows(
                CommonException.class,
                () -> nullCodec.decode("ALIPAY_JSAPI", "payload", nullCodec.checksum("payload")));
        var throwingCodec = new RegistryChannelActionCodec(List.of(new ThrowingCodec()));
        assertThrows(
                CommonException.class,
                () -> throwingCodec.decode("ALIPAY_JSAPI", "payload", throwingCodec.checksum("payload")));
    }

    @Test
    void rejectsOversizedEncodedPayload() {
        var codec = new RegistryChannelActionCodec(List.of(new LargeEncodingCodec()));
        assertEquals(
                ACTION_SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> codec.encode(ACTION)).getResultCode());
    }

    private static final class TestCodec implements ChannelActionTypeCodec<AlipayJsapiChannelAction> {
        private final String kind;
        private final AlipayJsapiChannelAction action;

        private TestCodec(String kind, AlipayJsapiChannelAction action) {
            this.kind = kind;
            this.action = action;
        }

        @Override
        public String kind() {
            return kind;
        }

        @Override
        public Class<AlipayJsapiChannelAction> actionType() {
            return AlipayJsapiChannelAction.class;
        }

        @Override
        public String encode(AlipayJsapiChannelAction ignored) {
            return "payload";
        }

        @Override
        public AlipayJsapiChannelAction decode(String payload) {
            return action;
        }
    }

    private static final class NullDecodingCodec implements ChannelActionTypeCodec<AlipayJsapiChannelAction> {
        @Override
        public String kind() {
            return "ALIPAY_JSAPI";
        }

        @Override
        public Class<AlipayJsapiChannelAction> actionType() {
            return AlipayJsapiChannelAction.class;
        }

        @Override
        public String encode(AlipayJsapiChannelAction action) {
            return "payload";
        }

        @Override
        public AlipayJsapiChannelAction decode(String payload) {
            return null;
        }
    }

    private static final class ThrowingCodec implements ChannelActionTypeCodec<AlipayJsapiChannelAction> {
        @Override
        public String kind() {
            return "ALIPAY_JSAPI";
        }

        @Override
        public Class<AlipayJsapiChannelAction> actionType() {
            return AlipayJsapiChannelAction.class;
        }

        @Override
        public String encode(AlipayJsapiChannelAction action) {
            return "payload";
        }

        @Override
        public AlipayJsapiChannelAction decode(String payload) {
            throw new IllegalStateException("synthetic");
        }
    }

    private static final class LargeEncodingCodec implements ChannelActionTypeCodec<AlipayJsapiChannelAction> {
        @Override
        public String kind() {
            return "ALIPAY_JSAPI";
        }

        @Override
        public Class<AlipayJsapiChannelAction> actionType() {
            return AlipayJsapiChannelAction.class;
        }

        @Override
        public String encode(AlipayJsapiChannelAction action) {
            return "x".repeat(RegistryChannelActionCodec.MAX_PAYLOAD_LENGTH + 1);
        }

        @Override
        public AlipayJsapiChannelAction decode(String payload) {
            return ACTION;
        }
    }
}
