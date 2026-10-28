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
package io.github.loadup.pay.channel.wechat.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelStatementReceipt;
import io.github.loadup.pay.channel.api.model.ChannelStatementScope;
import io.github.loadup.pay.channel.api.model.VerifiedChannelStatement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface WechatStatementConverter {
    @Mapping(target = "bindingRevisionId", source = "context.binding.id")
    @Mapping(target = "scope", source = "scope")
    @Mapping(target = "receipt", source = "receipt")
    @Mapping(target = "content", source = "content")
    VerifiedChannelStatement toResult(ChannelExecutionContext context, ChannelStatementScope scope, ChannelStatementReceipt receipt, byte[] content);
}
