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

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.pay.realtime.domain.model.*;
import java.util.*;

public interface TradeQueryGateway {
    Optional<TradeOrderProjection> findOrder(String tenant, String payee, String orderId);
    Optional<TradeRefundSummary> findRefundSummary(String tenant, String payee, String orderId);
    Map<String, TradeRefundSummary> refundSummaries(String tenant, String payee, Set<String> orderIds);
    PageResult<TradeOrderProjection> orders(String tenant, TradePageFilter filter);
    PageResult<TradePaymentProjection> payments(String tenant, TradePageFilter filter);
    PageResult<TradeRefundProjection> refunds(String tenant, TradePageFilter filter);
}
