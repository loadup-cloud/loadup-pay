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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public record AlipaySignedRequest(AlipayMethod method, @DiagnosticHidden Map<String, String> parameters) {
    public AlipaySignedRequest { parameters = Map.copyOf(parameters); }
    public String formBody() {
        return parameters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
