/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.app.model;

import io.github.loadup.commons.json.ToStringAsJson;

/**
 * Business decision about the current wake-up, not a new delivery attempt.
 */
public record MerchantNotificationWakeDecision(String result, String nativeTaskId) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
