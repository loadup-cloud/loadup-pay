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
package io.github.loadup.payment.app.port;

import io.github.loadup.payment.app.model.limitcenter.command.LimitBeginExecutionCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitConfirmCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitExecutionVerifyCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitRefundRestoreCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitReleaseCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitReserveCommand;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionPermitDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionVerificationDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReleaseDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReservationDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitRestoreDTO;

public interface PaymentLimitClient {
    LimitReservationDTO reserve(LimitReserveCommand command);

    LimitExecutionPermitDTO begin(LimitBeginExecutionCommand command);

    LimitExecutionVerificationDTO verify(LimitExecutionVerifyCommand command);

    LimitReservationDTO confirm(LimitConfirmCommand command);

    LimitReleaseDTO release(LimitReleaseCommand command);

    LimitRestoreDTO restoreRefund(LimitRefundRestoreCommand command);
}
