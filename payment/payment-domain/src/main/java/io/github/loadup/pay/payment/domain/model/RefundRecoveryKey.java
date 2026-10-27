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
package io.github.loadup.pay.payment.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;

/** Typed refund continuation identity; canonical parsing belongs to the module-local protocol. */
public record RefundRecoveryKey(String requestId, long generation) {
    public RefundRecoveryKey { new RecoveryTaskKey(requestId, generation); }
    public RecoveryTaskKey taskKey() { return new RecoveryTaskKey(requestId, generation); }
    public String bizId() { return taskKey().bizId(); }
    public static RefundRecoveryKey parse(String value) {
        var key = RecoveryTaskKey.parse(value);
        return new RefundRecoveryKey(key.subjectId(), key.generation());
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
