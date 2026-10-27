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

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.pay.payment.domain.model.*;
import java.util.Optional;

public interface PaymentCaptureGateway {
    Optional<PaymentCapture> findRequest(String tenant, String caller, String requestKey);
    Optional<PaymentCapture> find(String tenant, String id);
    Optional<PaymentCapture> lock(String tenant, String id);
    void insert(String tenant, PaymentCapture value);
    void complete(String tenant, PaymentCapture original, PaymentCapture complete, PaymentCaptureRead read);
    PageResult<PaymentLedgerLine> page(String tenant, PaymentCapture capture, int page, int size);
}
