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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.net.InetAddress;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * A reported payer IP literal; never performs DNS lookup or trusts forwarding headers.
 */
public record ChannelH5Input(@DiagnosticHidden String payerClientIp,
                             H5SceneType sceneType) implements ChannelPaymentInput {
    public ChannelH5Input {
        require(sceneType != null && validIp(payerClientIp), INVALID_ARGUMENT);
    }

    private static boolean validIp(String value) {
        if (value == null || value.isBlank() || value.length() > 45 || value.indexOf('%') >= 0
                || value.indexOf('[') >= 0 || value.indexOf(']') >= 0) return false;
        try {
            var address = InetAddress.ofLiteral(value);
            return !address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isMulticastAddress();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
