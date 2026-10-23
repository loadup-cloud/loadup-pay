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
package io.github.loadup.pay.limitcenter.domain.gateway;

import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitSettlementReceipt;

import java.util.List;
import java.util.Optional;

public interface LimitSettlementGateway {
    Optional<LimitSettlementReceipt> findReceipt(String tenant, String operationId);

    void insertReceipt(String tenant, LimitSettlementReceipt receipt);

    void fenceGate(String tenant, LimitOperationGate gate);

    void updateItems(String tenant, List<LimitReservationItem> before, List<LimitReservationItem> after);
}
