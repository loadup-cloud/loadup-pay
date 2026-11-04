/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.payment.domain.gateway;

import io.github.loadup.payment.domain.model.OrderRefundSummary;
import java.util.Optional;

/**
 * Caller already owns the parent gate and refund write locks in the same transaction.
 */
public interface RefundSummaryGateway {
    Optional<OrderRefundSummary> lock(String orderId);

    void insert(OrderRefundSummary value);

    void update(OrderRefundSummary before, OrderRefundSummary value);
}
