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
package io.github.loadup.pay.channel.alipay.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.channel.api.model.AlipayH5ChannelAction;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.util.Map;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * Named external HTTP operations; there is no internal-service HTTP fallback or automatic retry.
 */
public class AlipayProtocolClient {
    private final HttpTemplate http;
    private final AlipayCredentialReader credentials;
    private final AlipayProtocolCodec codec;

    public AlipayProtocolClient(HttpTemplate http, AlipayCredentialReader credentials, AlipayProtocolCodec codec) {
        this.http = http;
        this.credentials = credentials;
        this.codec = codec;
    }

    /**
     * Page generation has no network response and must never expose delegated authorization.
     */
    public String directWapUrl(String tenant, ChannelBindingRevision binding, URI gateway,
                               String businessJson, Map<String, String> options) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (binding == null || binding.accessMode() != AccessMode.DIRECT)
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        if (!AlipayH5ChannelAction.isGateway(gateway) || gateway.getRawQuery() != null)
            throw new CommonException(INVALID_ARGUMENT);
        var credential = credentials.read(tenant, binding);
        if (credential.appAuthToken() != null) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        var signed = codec.sign(AlipayMethod.WAP_PAY, credential, businessJson, options);
        return gateway.toASCIIString() + "?" + signed.formBody();
    }

    public AlipayVerifiedResponse call(String tenant, ChannelBindingRevision binding, AlipayMethod method,
                                       String businessJson, Map<String, String> options) {
        return exchangeVerified(tenant, binding, method, businessJson, options).response();
    }

    /**
     * Exact verified envelope for private source provenance. Credentials/signing still resolve exactly once per API call.
     */
    public AlipayVerifiedExchange exchangeVerified(String tenant, ChannelBindingRevision binding, AlipayMethod method,
                                                   String businessJson, Map<String, String> options) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (method == AlipayMethod.WAP_PAY) throw new CommonException(INVALID_ARGUMENT);
        var credential = credentials.read(tenant, binding);
        var request = codec.sign(method, credential, businessJson, options);
        var body = request.formBody();
        try {
            var response = http.exchange("alipay", method.operation(),
                    new HttpCall(Map.of(), Map.of(), Map.of("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8"), body));
            if (response == null || !response.getStatusCode().is2xxSuccessful())
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            var verified = codec.verify(method, credential, response.getBody());
            return new AlipayVerifiedExchange(verified, io.github.loadup.commons.util.DigestUtils.sha256(body), response.getBody());
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
