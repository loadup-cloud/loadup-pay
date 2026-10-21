/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.product.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.domain.model.MerchantEligibilityFacts;
import io.github.loadup.pay.product.domain.model.OrderAdmission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface OrderAdmissionConverter {
    @Mapping(target = "terms", source = "terms")
    @Mapping(target = "admissionId", source = "model.id")
    @Mapping(target = "contractId", source = "model.contract.contractId")
    @Mapping(target = "revision", expression = "java(model.contract() == null ? null : model.contract().revision())")
    @Mapping(
            target = "generation",
            expression = "java(model.contract() == null ? null : model.contract().generation())")
    @Mapping(target = "snapshotHash", source = "model.contract.snapshotHash")
    @Mapping(target = "compiledTermsHash", source = "model.contract.compiledTermsHash")
    OrderAdmissionDTO toDTO(OrderAdmission model, AcquiringTermsDTO terms);

    @Mapping(target = "payeeMerchantId", source = "payee")
    @Mapping(target = "eligible", source = "result.allowed")
    @Mapping(target = "reasonCode", source = "result.reasonCode")
    @Mapping(target = "merchantVersion", source = "result.merchantFactVersion")
    @Mapping(target = "authorizationVersion", source = "result.grantVersion")
    MerchantEligibilityFacts toFacts(String payee, MerchantEligibilityDTO result);

    default OffsetDateTime external(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
