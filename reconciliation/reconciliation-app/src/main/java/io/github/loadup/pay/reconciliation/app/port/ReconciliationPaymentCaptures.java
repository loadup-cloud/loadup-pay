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
package io.github.loadup.pay.reconciliation.app.port;

import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;

/**
 * Network calls only, with a trusted tenant and the reconciliation service credential; no local transaction.
 */
public interface ReconciliationPaymentCaptures {
    PaymentCaptureDTO create(String tenant, PaymentCaptureCreateCommand command);

    PaymentCaptureDTO retry(String tenant, io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand command);

    PaymentCaptureDTO detail(String tenant, String captureId);

    PageDTO<PaymentLedgerLineDTO> lines(String tenant, PaymentCaptureLinesQuery query);
}
