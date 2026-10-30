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

import java.util.List;
import java.util.Map;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;

/**
 * Uses only named loadup HTTP operations. No automatic retries, redirects or SDK-owned transport.
 */
public class WechatProtocolClient {
    private final HttpTemplate http;
    private final WechatCredentialReader credentials;
    private final WechatProtocolCodec codec;

    public WechatProtocolClient(HttpTemplate http, WechatCredentialReader credentials, WechatProtocolCodec codec) {
        this.http = http;
        this.credentials = credentials;
        this.codec = codec;
    }

    public WechatVerifiedResponse call(String tenant, ChannelBindingRevision binding, WechatMethod method,
                                       Map<String, String> path, Map<String, List<String>> query, String body) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (method == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var original = credentials.read(tenant, binding);
        var call = new HttpCall(path, query, Map.of("Accept", "application/json", "Content-Type", "application/json"), body);
        try {
            var response = http.exchange("wechat", method.operation(), call,
                    (verb, uri, bytes) -> codec.sign(method, original, verb, uri, bytes));
            // Error responses also require the original platform pin; unsigned errors cannot become financial evidence.
            return codec.verify(method, original, response);
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
