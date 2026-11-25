/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.components.payment.channel.api.codec;

import io.github.loadup.components.payment.channel.api.model.ChannelPaymentAction;

/**
 * Vendor-owned codec for exactly one payment action type. The registry enforces the common payload size, checksum and
 * whitespace-free discriminator rules; implementations own strict wire JSON and record validation.
 */
public interface ChannelActionTypeCodec<A extends ChannelPaymentAction> {
    /**
     * Stable snapshot discriminator, for example ALIPAY_JSAPI.
     */
    String kind();

    /**
     * The single concrete action type handled by this codec.
     */
    Class<A> actionType();

    /**
     * Encode one action without checksum or size framing.
     */
    String encode(A action);

    /**
     * Decode strict wire JSON into one action. Unknown, duplicate or trailing fields must be rejected.
     */
    A decode(String payload);
}
