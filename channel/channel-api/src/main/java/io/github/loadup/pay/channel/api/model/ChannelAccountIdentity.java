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

import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;

/**
 * Typed account semantics shared by bindings and providers, independent of payment orchestration.
 */
public sealed interface ChannelAccountIdentity permits AlipayAccountIdentity, WechatAccountIdentity {
    Channel channel();

    AccessMode accessMode();

    /**
     * The actual collecting account, independent of app aliases and credential versions.
     */
    String collectingAccountId();
}
