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
package io.github.loadup.pay.realtime.app.port;

import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.realtime.domain.model.TradeOrderProjection;
import io.github.loadup.pay.realtime.domain.model.TradePaymentProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundSummary;

import java.time.LocalDateTime;

public interface TradeProjectionCodec {
    String encode(TradeOrderProjection value);

    String encode(TradePaymentProjection value);

    String encode(TradeRefundProjection value);

    String encode(TradeRefundSummary value);

    TradeOrderProjection order(String payload, String hash, LocalDateTime snapshotAt);

    TradePaymentProjection payment(String payload, String hash, LocalDateTime snapshotAt);

    TradeRefundProjection refund(String payload, String hash, LocalDateTime snapshotAt);

    TradeRefundSummary summary(String payload, String hash, LocalDateTime snapshotAt);

    String hash(String payload);

    String requestDigest(String tenant, String service, PayEventEnvelope<?> event, String factsHash);
}
