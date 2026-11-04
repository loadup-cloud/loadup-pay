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
package io.github.loadup.productcenter.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.productcenter.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.productcenter.client.dto.FeeTermsDTO;
import io.github.loadup.productcenter.client.dto.OrderAdmissionDTO;
import io.github.loadup.productcenter.client.enums.AdmissionDecision;
import io.github.loadup.productcenter.client.enums.FeeRefundPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProductHttpContractTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();

    @Test
    void admissionRetainsOriginalIntentMinorUnitsAndAnAbsoluteMicrosecondDeadline() {
        var due = OffsetDateTime.parse("2026-11-02T12:30:00.123456+08:00");
        var command = new OrderAdmissionAuthorizeCommand(
                "order", "merchant", "scope", "a".repeat(64), 9007199254740993L, "CNY", due);
        var decoded = json.readValue(json.writeValueAsBytes(command), OrderAdmissionAuthorizeCommand.class);
        assertThat(decoded).isEqualTo(command);
        assertThat(decoded.requestedDueAt().toInstant()).isEqualTo(due.toInstant());
        var denial = new OrderAdmissionDTO(
                "admission",
                "order",
                "merchant",
                AdmissionDecision.DENIED,
                "PRODUCT_CONTRACT_UNAVAILABLE",
                command.requestDigest(),
                null,
                null,
                null,
                null,
                null,
                due,
                null,
                null);
        var reply = json.readValue(json.writeValueAsBytes(denial), OrderAdmissionDTO.class);
        assertThat(reply.decision()).isEqualTo(AdmissionDecision.DENIED);
        assertThat(reply.revision()).isNull();
        assertThat(reply.generation()).isNull();
        assertThat(reply.terms()).isNull();
        assertThat(reply.materializeBefore()).isNull();
        assertThat(reply.admittedAt().toInstant()).isEqualTo(due.toInstant());
    }

    @Test
    void feeRatesKeepDecimalPrecisionAndNullableCapsAcrossJson() {
        var fee = new FeeTermsDTO(
                new BigDecimal("0.123456789012345678901234567890"),
                0,
                RoundingMode.HALF_UP,
                0,
                null,
                FeeRefundPolicy.NO_RETURN);
        var decoded = json.readValue(json.writeValueAsBytes(fee), FeeTermsDTO.class);
        assertThat(decoded.rate()).isEqualByComparingTo(fee.rate());
        assertThat(decoded.maximumMinor()).isNull();
        assertThat(decoded.fixedMinor()).isZero();
        assertThat(decoded.refundPolicy()).isEqualTo(FeeRefundPolicy.NO_RETURN);
    }
}
