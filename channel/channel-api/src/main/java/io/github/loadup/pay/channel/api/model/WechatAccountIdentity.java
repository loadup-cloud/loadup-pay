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
package io.github.loadup.pay.channel.api.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record WechatAccountIdentity(String appId, String merchantId, String subAppId, String subMerchantId)
        implements ChannelAccountIdentity {
    public WechatAccountIdentity {
        DomainChecks.text(appId, 32);
        DomainChecks.text(merchantId, 32);
        if (subAppId != null) {
            DomainChecks.text(subAppId, 32);
        }
        if (subMerchantId != null) {
            DomainChecks.text(subMerchantId, 32);
        }
        require(subAppId == null || subMerchantId != null, INVALID_ARGUMENT);
    }

    @Override
    public Channel channel() {
        return Channel.WECHAT;
    }

    @Override
    public AccessMode accessMode() {
        return subMerchantId == null ? AccessMode.DIRECT : AccessMode.SERVICE_PROVIDER;
    }

    @Override
    public String collectingAccountId() {
        return subMerchantId == null ? merchantId : subMerchantId;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
