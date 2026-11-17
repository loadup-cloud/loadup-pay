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
package io.github.loadup.productcenter.app.converter;

import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.productcenter.app.model.ActivatedLimitPolicy;
import io.github.loadup.productcenter.client.dto.terms.AcquiringTermsDTO;
import io.github.loadup.productcenter.client.dto.terms.ChannelCapabilityDTO;
import io.github.loadup.productcenter.client.dto.terms.FeeTermsDTO;
import io.github.loadup.productcenter.client.dto.terms.LimitPolicyRefDTO;
import io.github.loadup.productcenter.client.dto.terms.NotificationTermsDTO;
import io.github.loadup.productcenter.client.dto.terms.OrderTermsDTO;
import io.github.loadup.productcenter.client.dto.terms.PaymentTermsDTO;
import io.github.loadup.productcenter.client.dto.terms.RefundTermsDTO;
import io.github.loadup.productcenter.domain.policy.ChannelPolicy;
import io.github.loadup.productcenter.domain.policy.FeePolicy;
import io.github.loadup.productcenter.domain.policy.LimitPolicy;
import io.github.loadup.productcenter.domain.policy.NotificationPolicy;
import io.github.loadup.productcenter.domain.policy.OrderPolicy;
import io.github.loadup.productcenter.domain.policy.PaymentPolicy;
import io.github.loadup.productcenter.domain.policy.RefundPolicy;
import io.github.loadup.productcenter.domain.policy.ValidatedCommercialPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface CommercialPolicyConverter {
    @Mapping(target = "schemaVersion", constant = "1")
    @Mapping(target = "order", source = "policy.order")
    @Mapping(target = "payment", source = "policy.payment")
    @Mapping(target = "fee", source = "policy.fee")
    @Mapping(target = "refund", source = "policy.refund")
    @Mapping(target = "notification", source = "policy.notification")
    @Mapping(target = "limits", expression = "java(toLimitReference(policy.limits(), activation))")
    @Mapping(target = "capabilities", source = "policy.channels.capabilities")
    @Mapping(target = "routingProfileRef", source = "policy.channels.routingProfileRef")
    @Mapping(target = "returnTargetCode", source = "policy.returnTargetCode")
    AcquiringTermsDTO toTerms(ValidatedCommercialPolicy policy, ActivatedLimitPolicy activation);

    OrderTermsDTO toOrder(OrderPolicy policy);

    PaymentTermsDTO toPayment(PaymentPolicy policy);

    FeeTermsDTO toFee(FeePolicy policy);

    RefundTermsDTO toRefund(RefundPolicy policy);

    NotificationTermsDTO toNotification(NotificationPolicy policy);

    ChannelCapabilityDTO toCapability(ChannelPolicy.Capability capability);

    @Mapping(target = "policyId", source = "policy.policyId")
    @Mapping(target = "policyVersion", source = "activation.policyVersion")
    @Mapping(target = "policyHash", source = "activation.policyHash")
    @Mapping(target = "parentAggregationEnabled", source = "policy.parentAggregationEnabled")
    @Mapping(target = "parentPolicyRef", source = "policy.parentPolicyRef")
    @Mapping(target = "amountRestore", source = "policy.amountRestore")
    @Mapping(target = "countRestore", source = "policy.countRestore")
    LimitPolicyRefDTO toLimitReference(LimitPolicy policy, ActivatedLimitPolicy activation);
}
