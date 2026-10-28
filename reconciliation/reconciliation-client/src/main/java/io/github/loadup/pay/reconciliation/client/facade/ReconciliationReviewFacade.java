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

import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewDecideCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewProposeCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewEventDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationSourceFileDTO;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewHistoryQuery;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewQuery;

import java.io.InputStream;

public interface ReconciliationReviewFacade {
    ReconciliationSourceFileDTO uploadEvidence(String filename, String type, long size, InputStream content);

    ReconciliationReviewEventDTO propose(ReconciliationReviewProposeCommand command);

    ReconciliationReviewEventDTO decide(ReconciliationReviewDecideCommand command);

    ReconciliationReviewDTO detail(ReconciliationReviewQuery query);

    PageDTO<ReconciliationReviewEventDTO> history(ReconciliationReviewHistoryQuery query);
}
