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
package io.github.loadup.payment.client.facade;

import io.github.loadup.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.payment.client.command.PaymentCloseCommand;
import io.github.loadup.payment.client.command.PaymentInputExpireCommand;
import io.github.loadup.payment.client.dto.ParentFenceDTO;
import io.github.loadup.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.payment.client.dto.PaymentInputExpiryDTO;
import io.github.loadup.payment.client.query.ParentFenceStatusQuery;

public interface PaymentControlFacade {
    PaymentInputExpiryDTO expireInput(PaymentInputExpireCommand command);

    PaymentCloseDTO close(PaymentCloseCommand command);

    ParentFenceDTO fenceParent(ParentPaymentFenceCommand command);

    ParentFenceDTO parentFenceStatus(ParentFenceStatusQuery query);
}
