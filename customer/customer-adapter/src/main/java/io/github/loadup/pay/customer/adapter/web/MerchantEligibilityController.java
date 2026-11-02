package io.github.loadup.pay.customer.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.facade.MerchantEligibilityFacade;
import io.github.loadup.pay.customer.client.http.CustomerHttpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal merchant eligibility")
public class MerchantEligibilityController {
    private final MerchantEligibilityFacade eligibility;

    public MerchantEligibilityController(MerchantEligibilityFacade eligibility) {
        this.eligibility = eligibility;
    }

    @PostMapping(CustomerHttpService.AUTHORIZE_PATH)
    @Operation(summary = "Check merchant eligibility using verified caller identity")
    public SuccessResponse<MerchantEligibilityDTO> authorize(
            @Valid @RequestBody MerchantActionAuthorizeCommand command) {
        return SuccessResponse.of(eligibility.authorize(command));
    }
}
