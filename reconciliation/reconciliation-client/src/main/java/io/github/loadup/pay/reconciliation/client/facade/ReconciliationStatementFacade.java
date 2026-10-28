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
import io.github.loadup.pay.reconciliation.client.command.ReconciliationStatementRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationStatementSubmitCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationStatementDTO;

public interface ReconciliationStatementFacade {
    ReconciliationStatementDTO submit(ReconciliationStatementSubmitCommand command);

    ReconciliationStatementDTO detail(IdQuery query);

    ReconciliationStatementDTO retry(ReconciliationStatementRetryCommand command);
}
