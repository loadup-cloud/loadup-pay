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
package io.github.loadup.pay.product.app.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;

/** No access token or policy payload is copied into the queue. */
public record PolicyActivationDispatch(String publicationId) {
    public static final String TYPE = "product.limit-policy.activate.v1";

    public PolicyActivationDispatch {
        DomainChecks.id(publicationId);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
