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

import io.github.loadup.pay.payment.domain.model.*;
import java.util.Optional;

public interface ChannelBindingManagementGateway {
    Optional<ChannelMerchantBinding> find(String tenant, String payee, String bindingCode);
    Optional<ChannelMerchantBinding> lock(String tenant, String payee, String bindingCode);
    Optional<StoredChannelBindingRevision> revision(String tenant, String revisionId);
    Optional<ChannelBindingChangeReceipt> receipt(String tenant, String bindingId, String requestKey);
    void insertHead(String tenant, ChannelMerchantBinding head);
    void updateHead(String tenant, ChannelMerchantBinding head, long expectedVersion);
    void insertRevision(String tenant, StoredChannelBindingRevision revision);
    void insertReceipt(String tenant, ChannelBindingChangeReceipt receipt);
}
