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
package io.github.loadup.pay.channel.wechat.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WechatMethodTest {
    @Test
    void refundQueryMatchesOnlyOneLegalDecodedRefundSegment() {
        assertTrue(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/refund%7Cpart%2A%40_-") );
        assertTrue(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/refund%2Apart"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/refund/part"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/%252F"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/%2F"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/%257C"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/%2e%2e"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/refund+part"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/%Q0"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/../refund"));
        assertFalse(WechatMethod.REFUND_QUERY.matches("/v3/refund/domestic/refunds/" + "a".repeat(65)));
    }
}
