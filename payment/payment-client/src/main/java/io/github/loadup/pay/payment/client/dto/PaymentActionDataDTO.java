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
package io.github.loadup.pay.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NoActionDTO.class, name = "NONE"),
    @JsonSubTypes.Type(value = WaitActionDTO.class, name = "WAIT"),
    @JsonSubTypes.Type(value = RedirectActionDTO.class, name = "REDIRECT"),
    @JsonSubTypes.Type(value = WechatJsapiActionDTO.class, name = "WECHAT_JSAPI"),
    @JsonSubTypes.Type(value = AlipayJsapiActionDTO.class, name = "ALIPAY_JSAPI")
})
public sealed interface PaymentActionDataDTO permits NoActionDTO, WaitActionDTO, RedirectActionDTO, WechatJsapiActionDTO, AlipayJsapiActionDTO {}
