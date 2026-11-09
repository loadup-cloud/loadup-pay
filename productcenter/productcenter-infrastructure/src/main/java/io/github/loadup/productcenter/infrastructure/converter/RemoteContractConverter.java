/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.productcenter.infrastructure.converter;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
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

    default SuccessResponse<io.github.loadup.productcenter.app.model.limitcenter.dto.LimitPolicyActivationDTO>
            fromLimitcenterLimitPolicyActivationDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.limitcenter.client.dto.LimitPolicyActivationDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.productcenter.app.model.merchantcenter.dto.MerchantEligibilityDTO>
            fromMerchantcenterMerchantEligibilityDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.productcenter.app.model.merchantcenter.dto.MerchantProfileDTO>
            fromMerchantcenterMerchantProfileDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }
}
