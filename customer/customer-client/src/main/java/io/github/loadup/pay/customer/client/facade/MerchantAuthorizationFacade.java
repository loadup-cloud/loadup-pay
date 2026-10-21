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
package io.github.loadup.pay.customer.client.facade;

import io.github.loadup.pay.customer.client.command.MerchantActionGrantCommand;
import io.github.loadup.pay.customer.client.command.MerchantActionGrantRevokeCommand;
import io.github.loadup.pay.customer.client.command.MerchantRelationshipCreateCommand;
import io.github.loadup.pay.customer.client.command.MerchantRelationshipRevokeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantActionGrantDTO;
import io.github.loadup.pay.customer.client.dto.MerchantRelationshipDTO;

/** Business use cases; identity and authorization come from a trusted invocation context. */
public interface MerchantAuthorizationFacade {
    MerchantRelationshipDTO createRelationship(MerchantRelationshipCreateCommand command);

    MerchantActionGrantDTO grant(MerchantActionGrantCommand command);

    MerchantRelationshipDTO revokeRelationship(MerchantRelationshipRevokeCommand command);

    MerchantActionGrantDTO revokeGrant(MerchantActionGrantRevokeCommand command);
}
