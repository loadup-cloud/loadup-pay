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

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WechatAccountIdentityDTO(
        @Schema(description = "Direct merchant appid or provider sp_appid") @NotBlank @Size(max = 32) String appId,
        @Schema(description = "Direct mchid or provider sp_mchid") @NotBlank @Size(max = 32) String merchantId,
        @Schema(description = "Provider sub_appid, when using the sub-merchant application") @Size(max = 32) String subAppId,
        @Schema(description = "Provider sub_mchid; absent in direct mode") @Size(max = 32) String subMerchantId)
        implements ChannelAccountIdentityDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
