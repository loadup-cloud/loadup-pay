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
package io.github.loadup.reconciliation.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.reconciliation.client.dto.ReconciliationInputDTO;
import io.github.loadup.reconciliation.client.enums.ReconciliationSide;
import io.github.loadup.reconciliation.client.enums.ReconciliationState;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ReconciliationHttpContractTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();

    private ReconciliationInputDTO input(ReconciliationSide side, String identity) {
        return new ReconciliationInputDTO(
                side, identity, "a".repeat(64), "b".repeat(64), identity + "-capture", "parser-v1", 0, 0, 0, 0);
    }

    @Test
    void frozenInputsKeepExplicitZeroTotalsAndRejectMissingFieldsOrOverflow() {
        var input = input(ReconciliationSide.CHANNEL, "statement");
        assertThat(json.readValue(json.writeValueAsBytes(input), ReconciliationInputDTO.class))
                .isEqualTo(input);
        var missing = (ObjectNode) json.valueToTree(input);
        missing.remove("paymentCount");
        assertThatThrownBy(() -> json.treeToValue(missing, ReconciliationInputDTO.class))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new ReconciliationInputDTO(
                        ReconciliationSide.CHANNEL,
                        "statement",
                        "a".repeat(64),
                        "b".repeat(64),
                        "source",
                        "parser",
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE))
                .isInstanceOf(CommonException.class);
    }

    @Test
    void initDoesNotInventACompletedRunAndMatchedRequiresZeroDifferences() {
        var initial = new ReconciliationBatchDTO(
                "batch",
                Channel.WECHAT,
                "a".repeat(64),
                LocalDate.of(2026, 11, 3),
                "profile",
                ReconciliationState.INIT,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null);
        assertThat(json.readValue(json.writeValueAsBytes(initial), ReconciliationBatchDTO.class))
                .isEqualTo(initial);
        var channel = input(ReconciliationSide.CHANNEL, "statement");
        var ledger = input(ReconciliationSide.LEDGER, "ledger");
        assertThatThrownBy(() -> new ReconciliationBatchDTO(
                        "batch",
                        Channel.WECHAT,
                        "a".repeat(64),
                        LocalDate.of(2026, 11, 3),
                        "profile",
                        ReconciliationState.MATCHED,
                        channel,
                        ledger,
                        "run",
                        1L,
                        1L,
                        "b".repeat(64),
                        3,
                        null,
                        null))
                .isInstanceOf(CommonException.class);
    }
}
