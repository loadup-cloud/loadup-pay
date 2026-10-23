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
package io.github.loadup.pay.payment.domain.gateway;

import io.github.loadup.pay.common.AccessMode;
import io.github.loadup.pay.common.Channel;
import io.github.loadup.pay.common.PaymentMethod;
import io.github.loadup.pay.payment.domain.model.ChannelBindingRevision;
import io.github.loadup.pay.payment.domain.model.RoutableChannelBinding;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence or integration port; implementations own transactions and trusted tenant isolation.
 */
public interface ChannelBindingGateway {
    /**
     * Lock route heads in a stable order before selecting a new attempt's immutable binding.
     */
    List<RoutableChannelBinding> lockActive(String tenantId, String payeeMerchantId, Channel channel,
                                            Set<AccessMode> allowedModes, PaymentMethod method);

    Optional<ChannelBindingRevision> findRevision(String tenantId, String bindingRevisionId);
}
