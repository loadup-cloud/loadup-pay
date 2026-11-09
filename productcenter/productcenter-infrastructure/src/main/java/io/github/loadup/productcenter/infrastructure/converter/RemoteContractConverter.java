package io.github.loadup.productcenter.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;

@Mapper(config = LoadUpMapStructConfig.class, subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface RemoteContractConverter {
    io.github.loadup.limitcenter.client.command.LimitPolicyActivateCommand toRemote(
            io.github.loadup.productcenter.app.model.limitcenter.command.LimitPolicyActivateCommand value);

    io.github.loadup.productcenter.app.model.limitcenter.command.LimitPolicyActivateCommand toLocal(
            io.github.loadup.limitcenter.client.command.LimitPolicyActivateCommand value);

    io.github.loadup.limitcenter.client.dto.LimitPolicyActivationDTO toRemote(
            io.github.loadup.productcenter.app.model.limitcenter.dto.LimitPolicyActivationDTO value);

    io.github.loadup.productcenter.app.model.limitcenter.dto.LimitPolicyActivationDTO toLocal(
            io.github.loadup.limitcenter.client.dto.LimitPolicyActivationDTO value);

    io.github.loadup.limitcenter.client.dto.LimitRuleDTO toRemote(
            io.github.loadup.productcenter.app.model.limitcenter.dto.LimitRuleDTO value);

    io.github.loadup.productcenter.app.model.limitcenter.dto.LimitRuleDTO toLocal(
            io.github.loadup.limitcenter.client.dto.LimitRuleDTO value);

    io.github.loadup.limitcenter.client.enums.LimitMetric toRemote(
            io.github.loadup.productcenter.app.model.limitcenter.enums.LimitMetric value);

    io.github.loadup.productcenter.app.model.limitcenter.enums.LimitMetric toLocal(
            io.github.loadup.limitcenter.client.enums.LimitMetric value);

    io.github.loadup.limitcenter.client.enums.LimitWindowType toRemote(
            io.github.loadup.productcenter.app.model.limitcenter.enums.LimitWindowType value);

    io.github.loadup.productcenter.app.model.limitcenter.enums.LimitWindowType toLocal(
            io.github.loadup.limitcenter.client.enums.LimitWindowType value);

    io.github.loadup.limitcenter.client.enums.RefundAmountRestorePolicy toRemote(
            io.github.loadup.productcenter.app.model.limitcenter.enums.RefundAmountRestorePolicy value);

    io.github.loadup.productcenter.app.model.limitcenter.enums.RefundAmountRestorePolicy toLocal(
            io.github.loadup.limitcenter.client.enums.RefundAmountRestorePolicy value);

    io.github.loadup.limitcenter.client.enums.RefundCountRestorePolicy toRemote(
            io.github.loadup.productcenter.app.model.limitcenter.enums.RefundCountRestorePolicy value);

    io.github.loadup.productcenter.app.model.limitcenter.enums.RefundCountRestorePolicy toLocal(
            io.github.loadup.limitcenter.client.enums.RefundCountRestorePolicy value);

    io.github.loadup.merchantcenter.client.command.MerchantActionAuthorizeCommand toRemote(
            io.github.loadup.productcenter.app.model.merchantcenter.command.MerchantActionAuthorizeCommand value);

    io.github.loadup.productcenter.app.model.merchantcenter.command.MerchantActionAuthorizeCommand toLocal(
            io.github.loadup.merchantcenter.client.command.MerchantActionAuthorizeCommand value);

    io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO toRemote(
            io.github.loadup.productcenter.app.model.merchantcenter.dto.MerchantEligibilityDTO value);

    io.github.loadup.productcenter.app.model.merchantcenter.dto.MerchantEligibilityDTO toLocal(
            io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO value);

    io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO toRemote(
            io.github.loadup.productcenter.app.model.merchantcenter.dto.MerchantProfileDTO value);

    io.github.loadup.productcenter.app.model.merchantcenter.dto.MerchantProfileDTO toLocal(
            io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO value);

    io.github.loadup.merchantcenter.client.enums.MerchantAction toRemote(
            io.github.loadup.productcenter.app.model.merchantcenter.enums.MerchantAction value);

    io.github.loadup.productcenter.app.model.merchantcenter.enums.MerchantAction toLocal(
            io.github.loadup.merchantcenter.client.enums.MerchantAction value);

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.productcenter.app.model.limitcenter.dto.LimitPolicyActivationDTO>
            fromLimitcenterLimitPolicyActivationDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.limitcenter.client.dto.LimitPolicyActivationDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.productcenter.app.model.merchantcenter.dto.MerchantEligibilityDTO>
            fromMerchantcenterMerchantEligibilityDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.productcenter.app.model.merchantcenter.dto.MerchantProfileDTO>
            fromMerchantcenterMerchantProfileDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }
}
