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
package io.github.loadup.payment.client.http;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.payment.client.command.PaymentAttemptStartCommand;
import io.github.loadup.payment.client.command.PaymentCloseCommand;
import io.github.loadup.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.payment.client.command.PaymentInputExpireCommand;
import io.github.loadup.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.payment.client.dto.ParentFenceDTO;
import io.github.loadup.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.payment.client.dto.PaymentInputDTO;
import io.github.loadup.payment.client.dto.PaymentInputExpiryDTO;
import io.github.loadup.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.payment.client.query.ParentFenceStatusQuery;
import io.github.loadup.payment.client.query.PaymentInputLookupQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface PaymentHttpService {
    String INPUT_PREPARE_PATH = "/api/internal/v1/payment/operations/input-prepare";
    String INPUT_LOOKUP_PATH = "/api/internal/v1/payment/operations/input-lookup";
    String INPUT_EXPIRE_PATH = "/api/internal/v1/payment/operations/input-expire";
    String ACCEPT_PATH = "/api/internal/v1/payment/operations/accept";
    String DETAIL_PATH = "/api/internal/v1/payment/operations/detail";
    String OUTCOME_PATH = "/api/internal/v1/payment/operations/outcome";
    String FACTS_PATH = "/api/internal/v1/payment/operations/facts";
    String CLOSE_PATH = "/api/internal/v1/payment/operations/close";
    String FENCE_PARENT_PATH = "/api/internal/v1/payment/operations/fence-parent";
    String PARENT_FENCE_STATUS_PATH = "/api/internal/v1/payment/operations/parent-fence-status";
    String START_ATTEMPT_PATH = "/api/internal/v1/payment/operations/start-attempt";
    String REFUND_SUMMARY_PATH = "/api/internal/v1/payment/operations/refund-summary";
    String REFUND_FACTS_PATH = "/api/internal/v1/payment/operations/refund-facts";

    @PostExchange(INPUT_PREPARE_PATH)
    SuccessResponse<PaymentInputDTO> inputPrepare(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentInputPrepareCommand request);

    @PostExchange(INPUT_LOOKUP_PATH)
    SuccessResponse<PaymentInputLookupDTO> inputLookup(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentInputLookupQuery request);

    @PostExchange(INPUT_EXPIRE_PATH)
    SuccessResponse<PaymentInputExpiryDTO> inputExpire(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentInputExpireCommand request);

    @PostExchange(ACCEPT_PATH)
    SuccessResponse<PaymentExecutionDTO> accept(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody PaymentExecuteCommand request);

    @PostExchange(DETAIL_PATH)
    SuccessResponse<PaymentExecutionDTO> detail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery request);

    @PostExchange(OUTCOME_PATH)
    SuccessResponse<PaymentOutcomeDTO> outcome(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery request);

    @PostExchange(FACTS_PATH)
    SuccessResponse<PaymentSettlementFactsDTO> facts(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery request);

    @PostExchange(CLOSE_PATH)
    SuccessResponse<PaymentCloseDTO> close(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody PaymentCloseCommand request);

    @PostExchange(FENCE_PARENT_PATH)
    SuccessResponse<ParentFenceDTO> fenceParent(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ParentPaymentFenceCommand request);

    @PostExchange(PARENT_FENCE_STATUS_PATH)
    SuccessResponse<ParentFenceDTO> parentFenceStatus(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ParentFenceStatusQuery request);

    @PostExchange(START_ATTEMPT_PATH)
    SuccessResponse<PaymentExecutionDTO> startAttempt(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentAttemptStartCommand request);

    @PostExchange(REFUND_SUMMARY_PATH)
    SuccessResponse<OrderRefundSummaryEventDTO> refundSummary(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery request);

    @PostExchange(REFUND_FACTS_PATH)
    SuccessResponse<RefundSettlementFactsDTO> refundFacts(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery request);
}
