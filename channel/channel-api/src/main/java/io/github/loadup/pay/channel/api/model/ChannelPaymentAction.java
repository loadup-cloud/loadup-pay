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

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import java.time.OffsetDateTime;

/** A payer interaction action, not financial success evidence. */
public sealed interface ChannelPaymentAction permits AlipayJsapiChannelAction, WechatJsapiChannelAction, WechatH5ChannelAction {
    Channel channel();
    PaymentMethod method();
    OffsetDateTime expiresAt();
}

