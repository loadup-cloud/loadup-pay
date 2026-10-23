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
package io.github.loadup.pay.payment.domain.gateway;

import io.github.loadup.pay.payment.domain.model.RefundRecoveryCursor;
import java.util.Optional;

/** Joins the original parent/request transaction; no independent execution claim. */
public interface RefundRecoveryGateway {
    Optional<RefundRecoveryCursor> lock(String tenant, String requestId);
    void insert(String tenant, RefundRecoveryCursor value);
    void update(String tenant, RefundRecoveryCursor before, RefundRecoveryCursor after);
}
