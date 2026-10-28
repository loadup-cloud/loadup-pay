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
package io.github.loadup.pay.channel.wechat.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Map;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_BINDING_MISMATCH;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;

/**
 * One original debit through a named HTTP operation, no transport retry or protocol fallback.
 */
public class WechatV2Client {
    private final HttpTemplate http;
    private final WechatCredentialReader credentials;
    private final WechatV2Codec codec;

    public WechatV2Client(HttpTemplate http, WechatCredentialReader credentials, WechatV2Codec codec) {
        this.http = http;
        this.credentials = credentials;
        this.codec = codec;
    }

    public WechatV2VerifiedResponse call(String tenant, ChannelBindingRevision binding, WechatV2Method method,
                                         Map<String, String> fields) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (method == null || fields == null) throw new CommonException(INVALID_ARGUMENT);
        if (tenant == null || !tenant.equals(io.github.loadup.commons.util.TenantUtil.getTenantId()))
            throw new CommonException(ACTION_FORBIDDEN);
        if (binding == null || binding.channel() != io.github.loadup.commons.payment.Channel.WECHAT
                || !(binding.identity() instanceof io.github.loadup.pay.channel.api.model.WechatAccountIdentity account))
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        if (!account.appId().equals(fields.get("appid")) || !account.merchantId().equals(fields.get("mch_id"))
                || !java.util.Objects.equals(account.subMerchantId(), fields.get("sub_mch_id"))
                || !java.util.Objects.equals(account.subAppId(), fields.get("sub_appid")))
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        byte[] key = credentials.apiV2Key(tenant, binding);
        try {
            var body = codec.encode(fields, key);
            var response = http.exchange("wechat-v2", method.operation(), new HttpCall(Map.of(), Map.of(),
                    Map.of("Content-Type", "application/xml;charset=UTF-8", "Accept", "application/xml"), body));
            if (response == null || !response.getStatusCode().is2xxSuccessful())
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return codec.verify(method, response.getBody(), key);
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        } finally {
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }
}
