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

import io.github.loadup.pay.payment.client.command.*;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.query.ChannelBindingDetailQuery;

public interface ChannelBindingFacade {
    ChannelBindingChangeDTO publish(ChannelBindingPublishCommand command);
    ChannelBindingChangeDTO suspend(ChannelBindingSuspendCommand command);
    ChannelBindingChangeDTO revoke(ChannelBindingRevokeCommand command);
    ChannelBindingDTO detail(ChannelBindingDetailQuery query);
}
