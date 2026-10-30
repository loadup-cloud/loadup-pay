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
import io.github.loadup.pay.reconciliation.app.port.ReconciliationStatementParser;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;

@Component
public class ReconciliationStatementParserAdapter implements ReconciliationStatementParser {
    private final WechatTradeStatementParser wechat;

    public ReconciliationStatementParserAdapter(WechatTradeStatementParser wechat) {
        this.wechat = wechat;
    }

    @Override
    public boolean supports(ReconciliationScope scope) {
        return scope != null && scope.channel() == Channel.WECHAT && WechatTradeStatementParser.PROFILE.equals(scope.statementProfile());
    }

    @Override
    public ReconciliationDataset parse(InputStream input, ReconciliationImportRequest request) {
        if (!supports(request.scope())) {
            throw new CommonException(INPUT_INVALID);
        }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (request.file().size() > 67108864L) {
            throw new CommonException(INPUT_INVALID);
        }
        try {
            int size = Math.toIntExact(request.file().size());
            byte[] bytes = input.readNBytes(size + 1);
            // A short transport read is not proof that the original pinned file has an invalid trailer.
            if (bytes.length != size) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            if (!request.file().sha256().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) {
                throw new CommonException(INPUT_INVALID);
            }
            return wechat.parse(new ByteArrayInputStream(bytes), request.scope(), request.statementAccount(), request.id(), request.id());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
