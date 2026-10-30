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
package io.github.loadup.pay.reconciliation.infrastructure.statement;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationEntryStatus;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WechatTradeStatementParserTest {
    private final WechatTradeStatementParser parser = new WechatTradeStatementParser(10, 16777216);
    private final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), WechatTradeStatementParser.PROFILE);
    private final ReconciliationStatementAccount direct = new ReconciliationStatementAccount("1234567890", null);

    @Test
    void paymentAndMultipleRefundsUseGrossAmountsAndKeepSnapshotRefundState() {
        var payment = payment();
        var refund = refund("refund-1", "PROCESSING");
        var other = refund("refund-2", "CHANGE");
        var data = parse(statement(List.of(payment, refund, other), "3", "9.00", "3.00", "1.00", "0.00", "10.00", "4.00"));
        assertEquals(3, data.records().size());
        assertEquals(new ReconciliationTotals(1, 1000, 2, 400), data.manifest().totals());
        assertEquals(ReconciliationSide.CHANNEL, data.manifest().side());
        assertEquals(1000, data.records().stream().filter(value -> value.key().kind() == ReconciliationRecordKind.PAYMENT).findFirst().orElseThrow().amount().getCent());
        assertEquals(java.util.Set.of(ReconciliationEntryStatus.PROCESSING, ReconciliationEntryStatus.UNKNOWN),
                data.records().stream().filter(value -> value.key().kind() == ReconciliationRecordKind.REFUND).map(ReconciliationLine::status).collect(java.util.stream.Collectors.toSet()));
        assertEquals(data, parse(statement(List.of(payment, refund, other), "3", "9.00", "3.00", "1.00", "0.00", "10.00", "4.00")));
    }

    @Test
    void eachTrailerTotalAndActualEofMustBePresentAndMatchAllRows() {
        var correct = statement(List.<String[]>of(payment()), "1", "9.00", "0.00", "0.00", "0.02", "10.00", "0.00");
        assertEquals(1, parse(correct).records().size());
        for (int index = 0; index < 7; index++) {
            String[] totals = {"1", "9.00", "0.00", "0.00", "0.02", "10.00", "0.00"};
            totals[index] = index == 0 ? "2" : "99.00";
            invalid(statement(List.<String[]>of(payment()), totals));
        }
        invalid(correct.substring(0, correct.lastIndexOf('\n', correct.length() - 2) + 1));
        invalid(correct + "unexpected\n");
        invalid(correct + "\n");
        invalid(correct.replace(WechatTradeStatementParser.HEADER, "old-version-header"));
    }

    @Test
    void mixedAccountsOtherDatesDuplicateKeysAndUnknownStatesRejectTheEntireInput() {
        var wrong = payment();
        wrong[2] = "9999999999";
        invalid(one(wrong));
        wrong = payment();
        wrong[3] = "1234567899";
        invalid(one(wrong));
        wrong = payment();
        wrong[0] = "2026-10-25 12:00:00";
        invalid(one(wrong));
        wrong = payment();
        wrong[0] = "2026-02-30 12:00:00";
        invalid(one(wrong));
        wrong = payment();
        wrong[9] = "REVOKED";
        invalid(one(wrong));
        wrong = payment();
        wrong[24] = "10.001";
        invalid(one(wrong));
        wrong = payment();
        wrong[11] = "USD";
        invalid(one(wrong));
        invalid(statement(List.of(payment(), payment()), "2", "18.00", "0.00", "0.00", "0.04", "20.00", "0.00"));
        var unknown = refund("refund-1", "UNKNOWN_NEW_STATE");
        invalid(statement(List.<String[]>of(unknown), "1", "0.00", "1.50", "0.50", "-0.01", "0.00", "2.00"));
    }

    @Test
    void providerScopeRequiresExactProviderAndActualSubMerchantOnEveryRow() {
        var row = payment();
        row[3] = "1234567899";
        var source = one(row).getBytes(StandardCharsets.UTF_8);
        var data = parser.parse(new ByteArrayInputStream(source), scope, new ReconciliationStatementAccount("1234567890", "1234567899"), "artifact", "capture");
        assertEquals(1, data.records().size());
        assertThrows(CommonException.class, () -> parser.parse(new ByteArrayInputStream(source), scope,
                new ReconciliationStatementAccount("1234567890", "1234567888"), "artifact", "capture"));
    }

    @Test
    void completeExplicitZeroTrailerIsRequiredForAnEmptyInputAndBytesAndRowsAreBounded() {
        assertEquals(0, parse(statement(List.of(), "0", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00")).records().size());
        invalid(WechatTradeStatementParser.HEADER + "\n");
        var two = statement(List.of(payment(), payment()), "2", "18.00", "0.00", "0.00", "0.04", "20.00", "0.00");
        assertThrows(CommonException.class, () -> new WechatTradeStatementParser(1, 16777216).parse(new ByteArrayInputStream(two.getBytes(StandardCharsets.UTF_8)), scope, direct, "artifact", "capture"));
        assertThrows(CommonException.class, () -> new WechatTradeStatementParser(10, 1024).parse(new ByteArrayInputStream(new byte[1025]), scope, direct, "artifact", "capture"));
        assertThrows(CommonException.class, () -> parser.parse(new ByteArrayInputStream(new byte[]{(byte) 0xc3, (byte) 0x28}), scope, direct, "artifact", "capture"));
    }

    private String one(String[] row) {
        return statement(List.<String[]>of(row), "1", "9.00", "0.00", "0.00", "0.02", "10.00", "0.00");
    }

    private String[] payment() {
        var row = new String[27];
        Arrays.fill(row, "");
        row[0] = "2026-10-26 12:00:00";
        row[1] = "wx-app";
        row[2] = "1234567890";
        row[3] = "0";
        row[5] = "transaction";
        row[6] = "request";
        row[9] = "SUCCESS";
        row[11] = "CNY";
        row[12] = "9.00";
        row[13] = "1.00";
        row[14] = "0";
        row[15] = "0";
        row[16] = "0.00";
        row[17] = "0.00";
        row[22] = "0.02";
        row[24] = "10.00";
        row[25] = "0.00";
        return row;
    }

    private String[] refund(String id, String status) {
        var row = payment();
        row[9] = "REFUND";
        row[12] = "0.00";
        row[13] = "0.00";
        row[14] = "wechat-" + id;
        row[15] = id;
        row[16] = "1.50";
        row[17] = "0.50";
        row[18] = "ORIGINAL";
        row[19] = status;
        row[22] = "-0.01";
        row[24] = "0.00";
        row[25] = "2.00";
        return row;
    }

    private String statement(List<String[]> rows, String... totals) {
        var out = new StringBuilder(WechatTradeStatementParser.HEADER).append('\n');
        for (var row : rows) {
            out.append(encoded(row)).append('\n');
        }
        return out.append(WechatTradeStatementParser.TOTAL_HEADER).append('\n').append(encoded(totals)).append('\n').toString();
    }

    private String encoded(String[] values) {
        return Arrays.stream(values).map(value -> "`" + value).collect(java.util.stream.Collectors.joining(","));
    }

    private ReconciliationDataset parse(String value) {
        return parser.parse(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)), scope, direct, "artifact", "capture");
    }

    private void invalid(String source) {
        assertEquals(INPUT_INVALID, assertThrows(CommonException.class, () -> parse(source)).getResultCode());
    }
}
