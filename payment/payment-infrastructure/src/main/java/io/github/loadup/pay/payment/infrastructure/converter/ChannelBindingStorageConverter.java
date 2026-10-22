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
package io.github.loadup.pay.payment.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.common.PaymentMethod;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.infrastructure.dataobject.*;
import org.mapstruct.*;
import java.util.Set;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ChannelBindingStorageConverter {
    ChannelMerchantBinding toHead(ChannelMerchantBindingDO row);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    ChannelMerchantBindingDO toHeadDO(String tenant, ChannelMerchantBinding value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "state")
    @Mapping(target = "currentRevisionId", source = "currentRevisionId")
    @Mapping(target = "currentRevision", source = "currentRevision")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "rowVersion", source = "rowVersion")
    ChannelMerchantBindingDO toHeadPatch(ChannelMerchantBinding value);

    @Mapping(target = "identity", source = "identity")
    @Mapping(target = "capabilities", source = "capabilities")
    ChannelBindingRevision toBinding(ChannelBindingRevisionDO row, ChannelAccountIdentity identity, Set<PaymentMethod> capabilities);

    @Mapping(target = "binding", source = "binding")
    StoredChannelBindingRevision toRevision(ChannelBindingRevisionDO row, ChannelBindingRevision binding);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.binding.id")
    @Mapping(target = "payeeMerchantId", source = "value.binding.payeeMerchantId")
    @Mapping(target = "channel", source = "value.binding.channel")
    @Mapping(target = "accessMode", source = "value.binding.accessMode")
    @Mapping(target = "accountScopeHash", source = "value.binding.accountScopeHash")
    @Mapping(target = "credentialReference", source = "value.binding.credentialReference")
    @Mapping(target = "credentialVersion", source = "value.binding.credentialVersion")
    @Mapping(target = "identityPayload", source = "identityPayload")
    @Mapping(target = "paymentCodeEnabled", expression = "java(value.binding().capabilities().contains(io.github.loadup.pay.common.PaymentMethod.PAYMENT_CODE) ? 1 : 0)")
    @Mapping(target = "jsapiEnabled", expression = "java(value.binding().capabilities().contains(io.github.loadup.pay.common.PaymentMethod.JSAPI) ? 1 : 0)")
    @Mapping(target = "h5Enabled", expression = "java(value.binding().capabilities().contains(io.github.loadup.pay.common.PaymentMethod.H5) ? 1 : 0)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    ChannelBindingRevisionDO toRevisionDO(String tenant, StoredChannelBindingRevision value, String identityPayload);

    ChannelBindingChangeReceipt toReceipt(ChannelBindingChangeReceiptDO row);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    ChannelBindingChangeReceiptDO toReceiptDO(String tenant, ChannelBindingChangeReceipt value);
}
