/*
 * #%L
 * LoadUp Pay Realtime Client
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
package io.github.loadup.pay.realtime.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class RealtimeHttpContractTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();

    @Test
    void genericEventKeepsAnExplicitZeroVersionAndAbsentSuccessTime() {
        var at = OffsetDateTime.parse("2026-11-03T12:00:00.123456+08:00");
        var data = new RefundSnapshotEventDTO(
                "refund",
                "request",
                "payment",
                "order",
                "merchant-sensitive",
                1000,
                "CNY",
                RefundState.INIT,
                at,
                null,
                0);
        var event = new EventEnvelope<>(
                "delivery", "event", "payment", "pay.payment.refund.realtime.v1", 1, "refund", 0, at, data);
        var decoded = json.readValue(
                json.writeValueAsBytes(event), new TypeReference<EventEnvelope<RefundSnapshotEventDTO>>() {});
        assertThat(decoded.aggregateVersion()).isZero();
        assertThat(decoded.data().state()).isEqualTo(RefundState.INIT);
        assertThat(decoded.data().succeededAt()).isNull();
        assertThat(decoded.occurredAt().toInstant()).isEqualTo(at.toInstant());
        assertThat(decoded.toString()).doesNotContain("merchant-sensitive");
        var missing = (ObjectNode) json.valueToTree(event);
        missing.remove("aggregateVersion");
        assertThatThrownBy(() -> json.readValue(
                        json.writeValueAsBytes(missing), new TypeReference<EventEnvelope<RefundSnapshotEventDTO>>() {}))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void ackKeepsOriginalDeliverySourceAndProjectionVersionsAndNeverTurnsARejectionIntoAcceptance() {
        var ack = new ProjectionAckDTO("delivery", "event", "payment", "refund", 0, 3, "STALE", false);
        var decoded = json.readValue(
                json.writeValueAsBytes(SuccessResponse.of(ack)),
                new TypeReference<SuccessResponse<ProjectionAckDTO>>() {});
        assertThat(decoded.data()).isEqualTo(ack);
        assertThat(decoded.data().accepted()).isFalse();
    }
}
