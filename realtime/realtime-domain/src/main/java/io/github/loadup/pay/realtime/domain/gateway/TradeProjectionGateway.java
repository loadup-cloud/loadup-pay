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
package io.github.loadup.pay.realtime.domain.gateway;

import io.github.loadup.pay.realtime.domain.model.ProjectionAdvance;
import io.github.loadup.pay.realtime.domain.model.TradeOrderProjection;
import io.github.loadup.pay.realtime.domain.model.TradePaymentProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundSummary;

import java.util.Optional;

/**
 * Persistence or integration port; implementations own transactions and trusted tenant isolation.
 */
public interface TradeProjectionGateway {
    Optional<TradeOrderProjection> findOrder(String tenantId, String orderId);

    Optional<TradeRefundSummary> findRefundSummary(String tenantId, String orderId);

    ProjectionAdvance applyOrderIfNewer(String tenantId, TradeOrderProjection snapshot, String factsHash);

    ProjectionAdvance applyPaymentIfNewer(String tenantId, TradePaymentProjection snapshot, String factsHash);

    ProjectionAdvance applyRefundIfNewer(String tenantId, TradeRefundProjection snapshot, String factsHash);

    ProjectionAdvance applyRefundSummaryIfNewer(String tenantId, TradeRefundSummary snapshot, String factsHash);
}
