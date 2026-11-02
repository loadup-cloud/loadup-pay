package io.github.loadup.pay.limitcenter.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand;
import io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery;
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
import io.github.loadup.pay.limitcenter.client.facade.LimitPolicyFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitReservationFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitExecutionFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitSettlementFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitRefundFacade;
import io.github.loadup.pay.limitcenter.client.http.LimitCenterHttpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal limit operations")
public class LimitCenterController {
    private final LimitPolicyFacade limitPolicy;
    private final LimitReservationFacade limitReservation;
    private final LimitExecutionFacade limitExecution;
    private final LimitSettlementFacade limitSettlement;
    private final LimitRefundFacade limitRefund;

    public LimitCenterController(
            LimitPolicyFacade limitPolicy,
            LimitReservationFacade limitReservation,
            LimitExecutionFacade limitExecution,
            LimitSettlementFacade limitSettlement,
            LimitRefundFacade limitRefund) {
        this.limitPolicy = limitPolicy;
        this.limitReservation = limitReservation;
        this.limitExecution = limitExecution;
        this.limitSettlement = limitSettlement;
        this.limitRefund = limitRefund;
    }

    @PostMapping(LimitCenterHttpService.ACTIVATE_PATH)
    @Operation(summary = "Activate a limit operation using verified service identity")
    public SuccessResponse<LimitPolicyActivationDTO> activate(@Valid @RequestBody LimitPolicyActivateCommand request) {
        return SuccessResponse.of(limitPolicy.activate(request));
    }

    @PostMapping(LimitCenterHttpService.RESERVE_PATH)
    @Operation(summary = "Reserve a limit operation using verified service identity")
    public SuccessResponse<LimitReservationDTO> reserve(@Valid @RequestBody LimitReserveCommand request) {
        return SuccessResponse.of(limitReservation.reserve(request));
    }

    @PostMapping(LimitCenterHttpService.DETAIL_PATH)
    @Operation(summary = "Detail a limit operation using verified service identity")
    public SuccessResponse<LimitReservationDTO> detail(@Valid @RequestBody LimitOperationQuery request) {
        return SuccessResponse.of(limitReservation.detail(request));
    }

    @PostMapping(LimitCenterHttpService.BEGIN_PATH)
    @Operation(summary = "Begin a limit operation using verified service identity")
    public SuccessResponse<LimitExecutionPermitDTO> begin(@Valid @RequestBody LimitBeginExecutionCommand request) {
        return SuccessResponse.of(limitExecution.beginExecution(request));
    }

    @PostMapping(LimitCenterHttpService.VERIFY_PATH)
    @Operation(summary = "Verify a limit operation using verified service identity")
    public SuccessResponse<LimitExecutionVerificationDTO> verify(@Valid @RequestBody LimitExecutionVerifyCommand request) {
        return SuccessResponse.of(limitExecution.verifyExecution(request));
    }

    @PostMapping(LimitCenterHttpService.EXPIRE_PATH)
    @Operation(summary = "Expire a limit operation using verified service identity")
    public SuccessResponse<LimitReservationDTO> expire(@Valid @RequestBody LimitExpireCommand request) {
        return SuccessResponse.of(limitExecution.expire(request));
    }

    @PostMapping(LimitCenterHttpService.CONFIRM_PATH)
    @Operation(summary = "Confirm a limit operation using verified service identity")
    public SuccessResponse<LimitReservationDTO> confirm(@Valid @RequestBody LimitConfirmCommand request) {
        return SuccessResponse.of(limitSettlement.confirm(request));
    }

    @PostMapping(LimitCenterHttpService.RELEASE_PATH)
    @Operation(summary = "Release a limit operation using verified service identity")
    public SuccessResponse<LimitReleaseDTO> release(@Valid @RequestBody LimitReleaseCommand request) {
        return SuccessResponse.of(limitSettlement.release(request));
    }

    @PostMapping(LimitCenterHttpService.RESTORE_REFUND_PATH)
    @Operation(summary = "RestoreRefund a limit operation using verified service identity")
    public SuccessResponse<LimitRestoreDTO> restoreRefund(@Valid @RequestBody LimitRefundRestoreCommand request) {
        return SuccessResponse.of(limitRefund.restoreRefund(request));
    }
}
