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
package io.github.loadup.pay.reconciliation.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Pinned private files and verified protocol metadata. Protocol instants are absolute epoch microseconds.
 */
public record ReconciliationOfficialReceipt(String bindingRevisionId, String format, String requestSha256,
                                            String officialHashType, String officialHash, long receivedEpochMicros,
                                            long downloadedEpochMicros,
                                            ReconciliationFileSnapshot content, ReconciliationFileSnapshot evidence) {
    public ReconciliationOfficialReceipt {
        DomainChecks.id(bindingRevisionId);
        DomainChecks.text(format, 64);
        require(requestSha256 != null && requestSha256.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        require("wechat-tradebill-grant-v1".equals(format) && "SHA1".equals(officialHashType)
                && officialHash != null && officialHash.matches("[0-9a-f]{40}"), INVALID_ARGUMENT);
        require(receivedEpochMicros > 0 && downloadedEpochMicros >= receivedEpochMicros, INVALID_ARGUMENT);
        require(content != null && content.size() <= 16777216 && evidence != null && evidence.size() <= 32768
                && !content.fileId().equals(evidence.fileId()), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
