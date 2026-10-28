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
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationStatementParserAdapterTest {
    private final ReconciliationStatementParserAdapter parser = new ReconciliationStatementParserAdapter(new WechatTradeStatementParser(10, 16777216));

    @Test
    void fullOriginalBytesAreVerifiedBeforeAnyCompletenessConclusion() throws Exception {
        var content = WechatTradeStatementParser.HEADER + "\n" + WechatTradeStatementParser.TOTAL_HEADER + "\n`0,`0.00,`0.00,`0.00,`0.00,`0.00,`0.00\n";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        var request = request(bytes);
        assertEquals(0L, parser.parse(new ByteArrayInputStream(bytes), request).manifest().totals().recordCount());
        assertEquals(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> parser.parse(new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, bytes.length - 1)), request)).getResultCode());
        byte[] changed = bytes.clone();
        changed[0] = 'x';
        assertEquals(INPUT_INVALID, assertThrows(CommonException.class, () -> parser.parse(new ByteArrayInputStream(changed), request)).getResultCode());
        byte[] invalid = content.replace("`0,`0.00,", "`1,`0.00,").getBytes(StandardCharsets.UTF_8);
        var bad = request(invalid);
        assertEquals(INPUT_INVALID, assertThrows(CommonException.class, () -> parser.parse(new ByteArrayInputStream(invalid), bad)).getResultCode());
    }

    private ReconciliationImportRequest request(byte[] bytes) throws Exception {
        var scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), WechatTradeStatementParser.PROFILE);
        return ReconciliationImportRequest.init("import", "batch", "ops", "request", "b".repeat(64), scope,
                new ReconciliationStatementAccount("1234567890", null), new ReconciliationFileSnapshot("file", "storage", bytes.length,
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))), "task");
    }
}
