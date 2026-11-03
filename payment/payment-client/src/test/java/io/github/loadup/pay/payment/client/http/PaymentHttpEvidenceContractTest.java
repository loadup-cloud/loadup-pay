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
package io.github.loadup.pay.payment.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.result.FailureResponse;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.result.PageResponse;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContracts;
import io.github.loadup.pay.payment.client.support.PaymentEvidenceContracts;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationOutcome;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PaymentHttpEvidenceContractTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    private final OffsetDateTime time = OffsetDateTime.parse("2026-11-02T12:00:00.123456+08:00");

    private PaymentCaptureDTO capture(PaymentCaptureState state, Long count, Long amount) {
        boolean initial = state == PaymentCaptureState.INIT;
        return new PaymentCaptureDTO(
                "capture",
                Channel.WECHAT,
                "a".repeat(64),
                LocalDate.of(2026, 11, 2),
                "wechat-trade-all-20260116",
                ZoneId.systemDefault().getId(),
                state,
                initial ? null : time,
                initial ? null : time.plusSeconds(1),
                count,
                amount,
                count,
                amount,
                initial ? null : "b".repeat(64),
                null,
                null,
                initial ? 0 : 1,
                "task",
                "PENDING",
                0,
                5);
    }

    private PaymentLedgerLineDTO line(long ordinal) {
        return new PaymentLedgerLineDTO(
                ordinal,
                PaymentLedgerKind.PAYMENT,
                "channel-transaction",
                null,
                "merchant-payment",
                9007199254740993L,
                "CNY",
                PaymentLedgerStatus.SUCCEEDED,
                time,
                "payment",
                "evidence",
                2);
    }

    @Test
    void captureRetainsExplicitZeroAndNullableInitialFactsWithoutInventingSealedTotals() {
        var initial = capture(PaymentCaptureState.INIT, null, null);
        var decoded = json.readValue(json.writeValueAsBytes(initial), PaymentCaptureDTO.class);
        assertThat(PaymentCaptureContracts.capture(decoded)).isEqualTo(initial);
        var sealed = capture(PaymentCaptureState.SEALED, 0L, 0L);
        assertThat(PaymentCaptureContracts.capture(
                        json.readValue(json.writeValueAsBytes(sealed), PaymentCaptureDTO.class)))
                .isEqualTo(sealed);
        assertThatThrownBy(() -> PaymentCaptureContracts.capture(capture(PaymentCaptureState.SEALED, null, null)))
                .isInstanceOf(RuntimeException.class);
        var missingVersion = json.valueToTree(initial).deepCopy();
        ((ObjectNode) missingVersion).remove("version");
        assertThatThrownBy(() -> json.treeToValue(missingVersion, PaymentCaptureDTO.class))
                .isInstanceOf(RuntimeException.class);
        ((ObjectNode) missingVersion).putNull("version");
        assertThatThrownBy(() -> json.treeToValue(missingVersion, PaymentCaptureDTO.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void flatPageEnvelopePreservesMetadataAndRejectsWrongOrdinalsOrMissingRequiredFields() {
        var page = PageDTO.of(List.of(line(2)), 2L, 2, 1);
        var wire = json.writeValueAsBytes(PageResponse.of(page));
        var decoded = json.readValue(wire, PaymentCapturePageResponse.class);
        assertThat(decoded.data()).hasSize(1);
        assertThat(PaymentCaptureContracts.page(
                                new PageDTO<>(decoded.data(), decoded.pageInfo()),
                                new PaymentCaptureLinesQuery("capture", 2, 1))
                        .getData())
                .containsExactly(line(2));
        assertThatThrownBy(() -> PaymentCaptureContracts.page(
                        PageDTO.of(List.of(line(1)), 2L, 2, 1), new PaymentCaptureLinesQuery("capture", 2, 1)))
                .isInstanceOf(RuntimeException.class);
        var malformed = json.valueToTree(line(1)).deepCopy();
        ((ObjectNode) malformed).remove("sourceVersion");
        assertThatThrownBy(() -> json.treeToValue(malformed, PaymentLedgerLineDTO.class))
                .isInstanceOf(RuntimeException.class);
        var failure = json.readValue(
                json.writeValueAsBytes(FailureResponse.of(PaymentResultCodeEnum.INVALID_STATE, "trace")),
                PaymentCapturePageResponse.class);
        assertThat(failure.result().getCode()).isEqualTo(PaymentResultCodeEnum.INVALID_STATE.getCode());
        assertThat(failure.data()).isNull();
        assertThat(failure.pageInfo()).isNull();
    }

    @Test
    void statementJsonRetainsBytesMicrosecondInstantsAndContentIntegrity() throws Exception {
        byte[] content = "original-statement-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] evidence = "original-provider-evidence".getBytes(StandardCharsets.UTF_8);
        var dto = new PaymentStatementDTO(
                "binding",
                Channel.WECHAT,
                "a".repeat(64),
                LocalDate.of(2026, 11, 2),
                "wechat-trade-all-20260116",
                "wechat-tradebill-grant-v1",
                "b".repeat(64),
                hash("SHA-256", evidence),
                "SHA1",
                hash("SHA-1", content),
                hash("SHA-256", content),
                time,
                time.plusSeconds(1),
                evidence,
                content);
        var decoded = PaymentEvidenceContracts.statement(
                json.readValue(json.writeValueAsBytes(dto), PaymentStatementDTO.class));
        assertThat(decoded.content()).isEqualTo(content);
        assertThat(decoded.evidence()).isEqualTo(evidence);
        assertThat(decoded.receivedAt().toInstant()).isEqualTo(time.toInstant());
        assertThat(decoded.toString()).doesNotContain("original-statement-bytes", "original-provider-evidence");
        var tampered = (ObjectNode) json.valueToTree(dto);
        tampered.put("contentSha256", "0".repeat(64));
        assertThatThrownBy(() -> json.treeToValue(tampered, PaymentStatementDTO.class))
                .isInstanceOf(RuntimeException.class);
    }

    private static String hash(String algorithm, byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    @Test
    void verificationRetainsExactMinorUnitsAndItsOriginalQueryWithoutCreatingAnExecutionReceipt() {
        var request = new PaymentVerificationQuery(
                "source-request",
                Channel.WECHAT,
                "a".repeat(64),
                PaymentLedgerKind.PAYMENT,
                "merchant-payment",
                "channel",
                null);
        var dto = new PaymentVerificationDTO(
                request,
                PaymentVerificationDisposition.OBSERVED,
                "binding",
                "payment",
                "attempt",
                null,
                2L,
                "SUCCEEDED",
                PaymentVerificationOutcome.SUCCEEDED,
                Money.ofMinor(9007199254740993L, "CNY"),
                "channel",
                null,
                time,
                "b".repeat(64),
                time,
                time.plusSeconds(1),
                null);
        var decoded = PaymentEvidenceContracts.verification(
                json.readValue(json.writeValueAsBytes(dto), PaymentVerificationDTO.class));
        assertThat(decoded.request()).isEqualTo(request);
        assertThat(decoded.amount().getCent()).isEqualTo(9007199254740993L);
        assertThat(decoded.amount().getCurrencyValue()).isEqualTo("CNY");
        assertThat(decoded.succeededAt().toInstant()).isEqualTo(time.toInstant());
    }

    @Test
    void sensitiveInteractionUsesItsExplicitSubtypeOnTheWireAndStaysHiddenInDiagnostics() {
        var command = new PaymentInputPrepareCommand(
                "payment",
                "order",
                "allocation",
                "merchant",
                100,
                "CNY",
                "input-key",
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.PAYMENT_CODE),
                new PaymentCodeInteractionDTO("synthetic-short-lived-code"));
        var decoded = json.readValue(json.writeValueAsBytes(command), PaymentInputPrepareCommand.class);
        assertThat(decoded.interaction()).isEqualTo(command.interaction());
        assertThat(decoded.toString()).doesNotContain("synthetic-short-lived-code");
        var malformed = (ObjectNode) json.valueToTree(command);
        ((ObjectNode) malformed.get("interaction")).put("type", "java.lang.Runtime");
        assertThatThrownBy(() -> json.treeToValue(malformed, PaymentInputPrepareCommand.class))
                .isInstanceOf(RuntimeException.class);
    }
}
