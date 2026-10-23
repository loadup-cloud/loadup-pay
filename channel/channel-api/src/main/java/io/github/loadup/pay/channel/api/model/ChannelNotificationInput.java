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
import io.github.loadup.commons.validation.DomainChecks;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Bounded untrusted wire bytes; parsers must reject duplicate fields and malformed encodings. */
public record ChannelNotificationInput(String contentType, @DiagnosticHidden byte[] body) {
    public static final int MAX_BODY_BYTES = 65536;
    public ChannelNotificationInput {
        DomainChecks.text(contentType, 256);
        require(body != null && body.length > 0 && body.length <= MAX_BODY_BYTES, INVALID_ARGUMENT);
        body = body.clone();
    }
    @Override public byte[] body() { return body.clone(); }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
