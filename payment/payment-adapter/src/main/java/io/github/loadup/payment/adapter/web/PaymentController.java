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
package io.github.loadup.payment.adapter.web;

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
import io.github.loadup.payment.client.facade.PaymentAcceptanceFacade;
import io.github.loadup.payment.client.facade.PaymentAttemptFacade;
import io.github.loadup.payment.client.facade.PaymentControlFacade;
import io.github.loadup.payment.client.facade.PaymentInputFacade;
import io.github.loadup.payment.client.facade.PaymentOutcomeFacade;
import io.github.loadup.payment.client.facade.PaymentSettlementFactsFacade;
import io.github.loadup.payment.client.facade.RefundSettlementFactsFacade;
import io.github.loadup.payment.client.facade.RefundSummaryFactsFacade;
import io.github.loadup.payment.client.http.PaymentHttpService;
import io.github.loadup.payment.client.query.ParentFenceStatusQuery;
import io.github.loadup.payment.client.query.PaymentInputLookupQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal operations")
public class PaymentController {
    private final PaymentInputFacade paymentInput;
    private final PaymentControlFacade paymentControl;
    private final PaymentAcceptanceFacade paymentAcceptance;
    private final PaymentOutcomeFacade paymentOutcome;
    private final PaymentSettlementFactsFacade paymentSettlementFacts;
    private final PaymentAttemptFacade paymentAttempt;
    private final RefundSummaryFactsFacade refundSummaryFacts;
    private final RefundSettlementFactsFacade refundSettlementFacts;

    public PaymentController(
            PaymentInputFacade paymentInput,
            PaymentControlFacade paymentControl,
            PaymentAcceptanceFacade paymentAcceptance,
            PaymentOutcomeFacade paymentOutcome,
            PaymentSettlementFactsFacade paymentSettlementFacts,
            PaymentAttemptFacade paymentAttempt,
            RefundSummaryFactsFacade refundSummaryFacts,
            RefundSettlementFactsFacade refundSettlementFacts) {
        this.paymentInput = paymentInput;
        this.paymentControl = paymentControl;
        this.paymentAcceptance = paymentAcceptance;
        this.paymentOutcome = paymentOutcome;
        this.paymentSettlementFacts = paymentSettlementFacts;
        this.paymentAttempt = paymentAttempt;
        this.refundSummaryFacts = refundSummaryFacts;
        this.refundSettlementFacts = refundSettlementFacts;
    }

    @PostMapping(PaymentHttpService.INPUT_PREPARE_PATH)
    @Operation(summary = "InputPrepare with verified service identity")
    public SuccessResponse<PaymentInputDTO> inputPrepare(@Valid @RequestBody PaymentInputPrepareCommand request) {
        return SuccessResponse.of(paymentInput.prepare(request));
    }

    @PostMapping(PaymentHttpService.INPUT_LOOKUP_PATH)
    @Operation(summary = "InputLookup with verified service identity")
    public SuccessResponse<PaymentInputLookupDTO> inputLookup(@Valid @RequestBody PaymentInputLookupQuery request) {
        return SuccessResponse.of(paymentInput.lookup(request));
    }

    @PostMapping(PaymentHttpService.INPUT_EXPIRE_PATH)
    @Operation(summary = "InputExpire with verified service identity")
    public SuccessResponse<PaymentInputExpiryDTO> inputExpire(@Valid @RequestBody PaymentInputExpireCommand request) {
        return SuccessResponse.of(paymentControl.expireInput(request));
    }

    @PostMapping(PaymentHttpService.ACCEPT_PATH)
    @Operation(summary = "Accept with verified service identity")
    public SuccessResponse<PaymentExecutionDTO> accept(@Valid @RequestBody PaymentExecuteCommand request) {
        return SuccessResponse.of(paymentAcceptance.accept(request));
    }

    @PostMapping(PaymentHttpService.DETAIL_PATH)
    @Operation(summary = "Detail with verified service identity")
    public SuccessResponse<PaymentExecutionDTO> detail(@Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(paymentAcceptance.detail(request));
    }

    @PostMapping(PaymentHttpService.OUTCOME_PATH)
    @Operation(summary = "Outcome with verified service identity")
    public SuccessResponse<PaymentOutcomeDTO> outcome(@Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(paymentOutcome.outcome(request));
    }

    @PostMapping(PaymentHttpService.FACTS_PATH)
    @Operation(summary = "Facts with verified service identity")
    public SuccessResponse<PaymentSettlementFactsDTO> facts(@Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(paymentSettlementFacts.paymentFacts(request));
    }

    @PostMapping(PaymentHttpService.CLOSE_PATH)
    @Operation(summary = "Close with verified service identity")
    public SuccessResponse<PaymentCloseDTO> close(@Valid @RequestBody PaymentCloseCommand request) {
        return SuccessResponse.of(paymentControl.close(request));
    }

    @PostMapping(PaymentHttpService.FENCE_PARENT_PATH)
    @Operation(summary = "FenceParent with verified service identity")
    public SuccessResponse<ParentFenceDTO> fenceParent(@Valid @RequestBody ParentPaymentFenceCommand request) {
        return SuccessResponse.of(paymentControl.fenceParent(request));
    }

    @PostMapping(PaymentHttpService.PARENT_FENCE_STATUS_PATH)
    @Operation(summary = "ParentFenceStatus with verified service identity")
    public SuccessResponse<ParentFenceDTO> parentFenceStatus(@Valid @RequestBody ParentFenceStatusQuery request) {
        return SuccessResponse.of(paymentControl.parentFenceStatus(request));
    }

    @PostMapping(PaymentHttpService.START_ATTEMPT_PATH)
    @Operation(summary = "StartAttempt with verified service identity")
    public SuccessResponse<PaymentExecutionDTO> startAttempt(@Valid @RequestBody PaymentAttemptStartCommand request) {
        return SuccessResponse.of(paymentAttempt.startAttempt(request));
    }

    @PostMapping(PaymentHttpService.REFUND_SUMMARY_PATH)
    @Operation(summary = "RefundSummary with verified service identity")
    public SuccessResponse<OrderRefundSummaryEventDTO> refundSummary(@Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(refundSummaryFacts.refundSummary(request));
    }

    @PostMapping(PaymentHttpService.REFUND_FACTS_PATH)
    @Operation(summary = "RefundFacts with verified service identity")
    public SuccessResponse<RefundSettlementFactsDTO> refundFacts(@Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(refundSettlementFacts.refundFacts(request));
    }
}
