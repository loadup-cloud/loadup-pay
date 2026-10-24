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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.validation.DomainChecks;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Native tasks identify a delivery and the number of previously started attempts. */
public record MerchantNotificationKey(String deliveryId, int sequence) {
    public MerchantNotificationKey { DomainChecks.id(deliveryId); require(sequence >= 0, INVALID_ARGUMENT); }
    public String bizId() { return deliveryId + ":" + sequence; }
    public static MerchantNotificationKey parse(String value) {
        require(value != null && value.matches("[^:]{1,64}:(0|[1-9][0-9]{0,9})"), INVALID_ARGUMENT);
        int split = value.lastIndexOf(':');
        return new MerchantNotificationKey(value.substring(0, split), Integer.parseInt(value.substring(split + 1)));
    }
    @Override public String toString() { return io.github.loadup.commons.json.ToStringAsJson.reflectionToString(this); }
}
