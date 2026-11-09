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
package io.github.loadup.payment.infrastructure.converter;

import io.github.loadup.components.payment.channel.api.model.ChannelAccountIdentity;
import io.github.loadup.components.payment.channel.api.model.ChannelBindingRevision;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.payment.domain.model.ChannelBindingChangeReceipt;
import io.github.loadup.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.payment.domain.model.StoredChannelBindingRevision;
import io.github.loadup.payment.infrastructure.dataobject.ChannelBindingChangeReceiptDO;
import io.github.loadup.payment.infrastructure.dataobject.ChannelBindingRevisionDO;
import io.github.loadup.payment.infrastructure.dataobject.ChannelMerchantBindingDO;
import java.util.Set;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ChannelBindingStorageConverter {
    ChannelMerchantBinding toHead(ChannelMerchantBindingDO row);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ChannelMerchantBindingDO toHeadDO(ChannelMerchantBinding value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "state")
    @Mapping(target = "currentRevisionId", source = "currentRevisionId")
    @Mapping(target = "currentRevision", source = "currentRevision")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "rowVersion", source = "rowVersion")
    ChannelMerchantBindingDO toHeadPatch(ChannelMerchantBinding value);

    @Mapping(target = "identity", source = "identity")
    @Mapping(target = "capabilities", source = "capabilities")
    ChannelBindingRevision toBinding(
            ChannelBindingRevisionDO row, ChannelAccountIdentity identity, Set<PaymentMethod> capabilities);

    @Mapping(target = "binding", source = "binding")
    StoredChannelBindingRevision toRevision(ChannelBindingRevisionDO row, ChannelBindingRevision binding);

    @Mapping(target = "id", source = "value.binding.id")
    @Mapping(target = "payeeMerchantId", source = "value.binding.payeeMerchantId")
    @Mapping(target = "channel", source = "value.binding.channel")
    @Mapping(target = "accessMode", source = "value.binding.accessMode")
    @Mapping(target = "accountScopeHash", source = "value.binding.accountScopeHash")
    @Mapping(target = "credentialReference", source = "value.binding.credentialReference")
    @Mapping(target = "credentialVersion", source = "value.binding.credentialVersion")
    @Mapping(target = "identityPayload", source = "identityPayload")
    @Mapping(
            target = "paymentCodeEnabled",
            expression =
                    "java(value.binding().capabilities().contains(io.github.loadup.components.payment.channel.contract.PaymentMethod.PAYMENT_CODE))")
    @Mapping(
            target = "jsapiEnabled",
            expression =
                    "java(value.binding().capabilities().contains(io.github.loadup.components.payment.channel.contract.PaymentMethod.JSAPI))")
    @Mapping(
            target = "h5Enabled",
            expression =
                    "java(value.binding().capabilities().contains(io.github.loadup.components.payment.channel.contract.PaymentMethod.H5))")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ChannelBindingRevisionDO toRevisionDO(StoredChannelBindingRevision value, String identityPayload);

    ChannelBindingChangeReceipt toReceipt(ChannelBindingChangeReceiptDO row);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ChannelBindingChangeReceiptDO toReceiptDO(ChannelBindingChangeReceipt value);
}
