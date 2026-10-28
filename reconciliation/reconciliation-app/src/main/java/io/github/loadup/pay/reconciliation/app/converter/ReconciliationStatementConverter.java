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
package io.github.loadup.pay.reconciliation.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationStatementDTO;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationOfficialReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationStatementConverter {
    @Mapping(target = "channel", source = "scope.channel")
    @Mapping(target = "accountScopeHash", source = "scope.accountScopeHash")
    @Mapping(target = "billDate", source = "scope.billDate")
    @Mapping(target = "statementProfile", source = "scope.statementProfile")
    PaymentStatementQuery toQuery(ReconciliationStatementRequest value);

    @Mapping(target = "bindingRevisionId", source = "source.bindingRevisionId")
    @Mapping(target = "format", source = "source.format")
    @Mapping(target = "requestSha256", source = "source.requestSha256")
    @Mapping(target = "officialHashType", source = "source.officialHashType")
    @Mapping(target = "officialHash", source = "source.officialHash")
    @Mapping(target = "receivedEpochMicros", source = "source.receivedAt", qualifiedByName = "epochMicros")
    @Mapping(target = "downloadedEpochMicros", source = "source.downloadedAt", qualifiedByName = "epochMicros")
    @Mapping(target = "content", source = "file")
    @Mapping(target = "evidence", source = "proof")
    ReconciliationOfficialReceipt toReceipt(PaymentStatementDTO source, ReconciliationFileSnapshot file, ReconciliationFileSnapshot proof);

    @Named("epochMicros")
    default long micros(OffsetDateTime value) {
        if (value == null || value.getNano() % 1000 != 0)
            throw new io.github.loadup.commons.error.CommonException(io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID);
        return Math.addExact(Math.multiplyExact(value.toEpochSecond(), 1000000L), value.getNano() / 1000L);
    }

    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "batchId", source = "value.batchId")
    @Mapping(target = "callerServiceId", source = "value.callerServiceId")
    @Mapping(target = "requestKey", source = "value.id", qualifiedByName = "officialKey")
    @Mapping(target = "requestDigest", source = "value.requestDigest")
    @Mapping(target = "scope", source = "value.scope")
    @Mapping(target = "statementAccount", source = "value.statementAccount")
    @Mapping(target = "file", source = "value.receipt.content")
    @Mapping(target = "taskId", source = "taskId")
    @Mapping(target = "sourceKind", constant = "OFFICIAL_DOWNLOAD")
    @Mapping(target = "state", constant = "INIT")
    @Mapping(target = "artifactId", ignore = true)
    @Mapping(target = "rejectionCode", ignore = true)
    @Mapping(target = "version", constant = "0L")
    @Mapping(target = "seal", ignore = true)
    @Mapping(target = "reject", ignore = true)
    ReconciliationImportRequest toImport(ReconciliationStatementRequest value, String taskId);

    @Named("officialKey")
    default String officialKey(String id) {
        return "official:" + id;
    }

    @Mapping(target = "sourceId", source = "value.id")
    @Mapping(target = "batchId", source = "value.batchId")
    @Mapping(target = "bindingRevisionId", source = "value.bindingRevisionId")
    @Mapping(target = "channel", source = "value.scope.channel")
    @Mapping(target = "accountScopeHash", source = "value.scope.accountScopeHash")
    @Mapping(target = "billDate", source = "value.scope.billDate")
    @Mapping(target = "statementProfile", source = "value.scope.statementProfile")
    @Mapping(target = "version", source = "value.version")
    @Mapping(target = "importId", expression = "java(value.state() == io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementState.READY ? value.id() : null)")
    @Mapping(target = "taskId", source = "value.taskId")
    @Mapping(target = "taskStatus", source = "task.status")
    @Mapping(target = "attempts", source = "task.attempts")
    @Mapping(target = "maxAttempts", source = "task.maxAttempts")
    @Mapping(target = "state", source = "value.state")
    ReconciliationStatementDTO toDTO(ReconciliationStatementRequest value, DelayedTask task);
}
