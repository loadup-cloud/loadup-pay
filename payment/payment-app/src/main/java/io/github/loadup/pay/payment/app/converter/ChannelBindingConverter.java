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
package io.github.loadup.pay.payment.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.payment.client.command.ChannelBindingPublishCommand;
import io.github.loadup.pay.payment.client.dto.AlipayAccountIdentityDTO;
import io.github.loadup.pay.payment.client.dto.ChannelAccountIdentityDTO;
import io.github.loadup.pay.payment.client.dto.ChannelBindingChangeDTO;
import io.github.loadup.pay.payment.client.dto.ChannelBindingDTO;
import io.github.loadup.pay.payment.client.dto.WechatAccountIdentityDTO;
import io.github.loadup.pay.payment.domain.model.ChannelBindingChangeReceipt;
import io.github.loadup.pay.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.pay.payment.domain.model.StoredChannelBindingRevision;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ChannelBindingConverter {
    AlipayAccountIdentity toIdentity(AlipayAccountIdentityDTO value);

    WechatAccountIdentity toIdentity(WechatAccountIdentityDTO value);

    AlipayAccountIdentityDTO toIdentityDTO(AlipayAccountIdentity value);

    WechatAccountIdentityDTO toIdentityDTO(WechatAccountIdentity value);

    default ChannelAccountIdentity toIdentity(ChannelAccountIdentityDTO value) {
        return switch (value) {
            case AlipayAccountIdentityDTO alipay -> toIdentity(alipay);
            case WechatAccountIdentityDTO wechat -> toIdentity(wechat);
        };
    }

    default ChannelAccountIdentityDTO toIdentityDTO(ChannelAccountIdentity value) {
        return switch (value) {
            case AlipayAccountIdentity alipay -> toIdentityDTO(alipay);
            case WechatAccountIdentity wechat -> toIdentityDTO(wechat);
        };
    }

    @Mapping(target = "id", source = "revisionId")
    ChannelBindingRevision toBinding(ChannelBindingPublishCommand command, String revisionId, String accountScopeHash);

    @Mapping(target = "binding", source = "binding")
    StoredChannelBindingRevision toStored(ChannelBindingRevision binding, String bindingId, int revision,
                                          String identityHash, String callerServiceId, LocalDateTime publishedAt);

    ChannelBindingChangeDTO toChange(ChannelBindingChangeReceipt value);

    @Mapping(target = "bindingId", source = "head.id")
    @Mapping(target = "payeeMerchantId", source = "head.payeeMerchantId")
    @Mapping(target = "bindingCode", source = "head.bindingCode")
    @Mapping(target = "channel", source = "head.channel")
    @Mapping(target = "accessMode", source = "head.accessMode")
    @Mapping(target = "state", source = "head.state")
    @Mapping(target = "bindingRevisionId", source = "head.currentRevisionId")
    @Mapping(target = "revision", source = "head.currentRevision")
    @Mapping(target = "capabilities", source = "revision.binding.capabilities")
    @Mapping(target = "priority", source = "head.priority")
    @Mapping(target = "version", source = "head.rowVersion")
    ChannelBindingDTO toDTO(ChannelMerchantBinding head, StoredChannelBindingRevision revision);
}
