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
package io.github.loadup.pay.payment.client.facade;

/** Stable identity of the conditional input-expiry close; retries never create a new control request. */
public final class PaymentInputExpiryPolicy {
    public static final String REQUEST_KEY_PREFIX = "input-expiry/";
    public static final String REASON = "INPUT_EXPIRED";
    public static final long FENCE_EPOCH = 1L;
    private PaymentInputExpiryPolicy() {}
}
