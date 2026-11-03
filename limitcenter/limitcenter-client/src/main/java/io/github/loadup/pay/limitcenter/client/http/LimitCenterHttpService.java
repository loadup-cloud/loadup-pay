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
package io.github.loadup.pay.limitcenter.client.http;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface LimitCenterHttpService {
    String ACTIVATE_PATH = "/api/internal/v1/limitcenter/operations/activate";
    String RESERVE_PATH = "/api/internal/v1/limitcenter/operations/reserve";
    String DETAIL_PATH = "/api/internal/v1/limitcenter/operations/detail";
    String BEGIN_PATH = "/api/internal/v1/limitcenter/operations/begin";
    String VERIFY_PATH = "/api/internal/v1/limitcenter/operations/verify";
    String EXPIRE_PATH = "/api/internal/v1/limitcenter/operations/expire";
    String CONFIRM_PATH = "/api/internal/v1/limitcenter/operations/confirm";
    String RELEASE_PATH = "/api/internal/v1/limitcenter/operations/release";
    String RESTORE_REFUND_PATH = "/api/internal/v1/limitcenter/operations/restore-refund";

    @PostExchange(ACTIVATE_PATH)
    SuccessResponse<LimitPolicyActivationDTO> activate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody LimitPolicyActivateCommand request);

    @PostExchange(RESERVE_PATH)
    SuccessResponse<LimitReservationDTO> reserve(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody LimitReserveCommand request);

    @PostExchange(DETAIL_PATH)
    SuccessResponse<LimitReservationDTO> detail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody LimitOperationQuery request);

    @PostExchange(BEGIN_PATH)
    SuccessResponse<LimitExecutionPermitDTO> begin(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody LimitBeginExecutionCommand request);

    @PostExchange(VERIFY_PATH)
    SuccessResponse<LimitExecutionVerificationDTO> verify(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody LimitExecutionVerifyCommand request);

    @PostExchange(EXPIRE_PATH)
    SuccessResponse<LimitReservationDTO> expire(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody LimitExpireCommand request);

    @PostExchange(CONFIRM_PATH)
    SuccessResponse<LimitReservationDTO> confirm(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody LimitConfirmCommand request);

    @PostExchange(RELEASE_PATH)
    SuccessResponse<LimitReleaseDTO> release(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody LimitReleaseCommand request);

    @PostExchange(RESTORE_REFUND_PATH)
    SuccessResponse<LimitRestoreDTO> restoreRefund(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody LimitRefundRestoreCommand request);
}
