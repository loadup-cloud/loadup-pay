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
package io.github.loadup.pay.reconciliation.client.facade;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchTaskDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationDifferenceDTO;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationDifferenceQuery;

/**
 * Operations use cases; tenant, caller and account authorization come from trusted invocation context.
 */
public interface ReconciliationFacade {
    ReconciliationBatchDTO create(ReconciliationBatchCreateCommand command);

    ReconciliationBatchDTO attach(ReconciliationInputAttachCommand command);

    ReconciliationBatchDTO start(ReconciliationBatchStartCommand command);

    ReconciliationBatchDTO retry(ReconciliationBatchRetryCommand command);

    ReconciliationBatchDTO detail(IdQuery query);

    ReconciliationBatchTaskDTO task(IdQuery query);

    PageDTO<ReconciliationDifferenceDTO> differences(ReconciliationDifferenceQuery query);
}
