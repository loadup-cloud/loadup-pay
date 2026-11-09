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
package io.github.loadup.limitcenter.infrastructure.converter;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(config = LoadUpMapStructConfig.class, subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface RemoteContractConverter {
    io.github.loadup.payment.client.dto.AlipayJsapiActionDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.AlipayJsapiActionDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.AlipayJsapiActionDTO toLocal(
            io.github.loadup.payment.client.dto.AlipayJsapiActionDTO value);

    io.github.loadup.payment.client.dto.NoActionDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.NoActionDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.NoActionDTO toLocal(
            io.github.loadup.payment.client.dto.NoActionDTO value);

    io.github.loadup.payment.client.dto.PaymentActionDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.PaymentActionDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.PaymentActionDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentActionDTO value);

    @SubclassMapping(
            source = io.github.loadup.limitcenter.app.model.payment.dto.NoActionDTO.class,
            target = io.github.loadup.payment.client.dto.NoActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.limitcenter.app.model.payment.dto.WaitActionDTO.class,
            target = io.github.loadup.payment.client.dto.WaitActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.limitcenter.app.model.payment.dto.RedirectActionDTO.class,
            target = io.github.loadup.payment.client.dto.RedirectActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.limitcenter.app.model.payment.dto.WechatJsapiActionDTO.class,
            target = io.github.loadup.payment.client.dto.WechatJsapiActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.limitcenter.app.model.payment.dto.AlipayJsapiActionDTO.class,
            target = io.github.loadup.payment.client.dto.AlipayJsapiActionDTO.class)
    io.github.loadup.payment.client.dto.PaymentActionDataDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.PaymentActionDataDTO value);

    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.NoActionDTO.class,
            target = io.github.loadup.limitcenter.app.model.payment.dto.NoActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.WaitActionDTO.class,
            target = io.github.loadup.limitcenter.app.model.payment.dto.WaitActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.RedirectActionDTO.class,
            target = io.github.loadup.limitcenter.app.model.payment.dto.RedirectActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.WechatJsapiActionDTO.class,
            target = io.github.loadup.limitcenter.app.model.payment.dto.WechatJsapiActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.AlipayJsapiActionDTO.class,
            target = io.github.loadup.limitcenter.app.model.payment.dto.AlipayJsapiActionDTO.class)
    io.github.loadup.limitcenter.app.model.payment.dto.PaymentActionDataDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentActionDataDTO value);

    io.github.loadup.payment.client.dto.PaymentExecutionDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.PaymentExecutionDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.PaymentExecutionDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentExecutionDTO value);

    io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.PaymentSettlementFactsDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.PaymentSettlementFactsDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO value);

    io.github.loadup.payment.client.dto.RedirectActionDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.RedirectActionDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.RedirectActionDTO toLocal(
            io.github.loadup.payment.client.dto.RedirectActionDTO value);

    io.github.loadup.payment.client.dto.RefundSettlementFactsDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.RefundSettlementFactsDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.RefundSettlementFactsDTO toLocal(
            io.github.loadup.payment.client.dto.RefundSettlementFactsDTO value);

    io.github.loadup.payment.client.dto.WaitActionDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.WaitActionDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.WaitActionDTO toLocal(
            io.github.loadup.payment.client.dto.WaitActionDTO value);

    io.github.loadup.payment.client.dto.WechatJsapiActionDTO toRemote(
            io.github.loadup.limitcenter.app.model.payment.dto.WechatJsapiActionDTO value);

    io.github.loadup.limitcenter.app.model.payment.dto.WechatJsapiActionDTO toLocal(
            io.github.loadup.payment.client.dto.WechatJsapiActionDTO value);

    io.github.loadup.payment.client.enums.PaymentActionType toRemote(
            io.github.loadup.limitcenter.app.model.payment.enums.PaymentActionType value);

    io.github.loadup.limitcenter.app.model.payment.enums.PaymentActionType toLocal(
            io.github.loadup.payment.client.enums.PaymentActionType value);

    default SuccessResponse<io.github.loadup.limitcenter.app.model.payment.dto.PaymentSettlementFactsDTO>
            fromPaymentPaymentSettlementFactsDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.limitcenter.app.model.payment.dto.RefundSettlementFactsDTO>
            fromPaymentRefundSettlementFactsDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.RefundSettlementFactsDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }
}
