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
package io.github.loadup.pay.reconciliation.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationStatementState;

import java.time.LocalDate;

/**
 * READY exposes the linked import identity; source files and private proof have no public download URL.
 */
public record ReconciliationStatementDTO(String sourceId, String batchId, String bindingRevisionId, Channel channel,
                                         String accountScopeHash, LocalDate billDate, String statementProfile,
                                         ReconciliationStatementState state,
                                         String importId, long version, String taskId, String taskStatus, int attempts,
                                         int maxAttempts) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
