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
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Named external HTTP operations; there is no internal-service HTTP fallback or automatic retry. */
public class AlipayProtocolClient {
    private final HttpTemplate http;
    private final AlipayCredentialReader credentials;
    private final AlipayProtocolCodec codec;
    public AlipayProtocolClient(HttpTemplate http, AlipayCredentialReader credentials, AlipayProtocolCodec codec) {
        this.http = http; this.credentials = credentials; this.codec = codec;
    }
    public AlipayVerifiedResponse call(String tenant, ChannelBindingRevision binding, AlipayMethod method,
            String businessJson, Map<String, String> options) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var credential = credentials.read(tenant, binding);
        var request = codec.sign(method, credential, businessJson, options);
        try {
            var response = http.exchange("alipay", method.operation(),
                    new HttpCall(Map.of(), Map.of(), Map.of("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8"), request.formBody()));
            if (response == null || !response.getStatusCode().is2xxSuccessful()) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return codec.verify(method, credential, response.getBody());
        } catch (CommonException failure) { throw failure; }
        catch (RuntimeException failure) { throw new CommonException(DEPENDENCY_UNAVAILABLE); }
    }
}
