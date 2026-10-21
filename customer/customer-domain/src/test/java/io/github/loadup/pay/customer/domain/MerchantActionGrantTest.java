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
package io.github.loadup.pay.customer.domain;

import io.github.loadup.pay.common.*;
import io.github.loadup.pay.customer.domain.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MerchantActionGrantTest {
    @Test
    void relationshipNeverSubstitutesForAnExplicitActionGrant() {
        var from = LocalDateTime.of(2026, 10, 1, 0, 0);
        var now = from.plusDays(1);
        var relation = new MerchantRelationship("relation", "parent", "child", GrantState.ACTIVE, from, null, 0);
        var grant = new MerchantActionGrant(
                "grant",
                PrincipalType.MERCHANT_APP,
                "app",
                "child",
                "relation",
                MerchantAction.CREATE_ORDER,
                GrantState.ACTIVE,
                from,
                null,
                0);
        assertTrue(
                grant.allows(PrincipalType.MERCHANT_APP, "app", "parent", "child", MerchantAction.CREATE_ORDER, now, relation));
        assertFalse(grant.allows(PrincipalType.MERCHANT_APP, "app", "parent", "child", MerchantAction.REFUND, now, relation));
        assertFalse(grant.allows(PrincipalType.MERCHANT_APP, "app", "parent", "child", MerchantAction.CREATE_ORDER, now, null));
    }

    @Test
    void delegatedPrincipalMustBelongToTheParentAndRevocationIsFinal() {
        var from = LocalDateTime.of(2026, 10, 1, 0, 0);
        var relation = MerchantRelationship.init("relation", "parent", "child", from, null).activate();
        var grant = MerchantActionGrant.init("grant", PrincipalType.MERCHANT_APP, "app", "child", "relation",
                MerchantAction.CREATE_ORDER, from, null).activate();
        assertFalse(grant.allows(PrincipalType.MERCHANT_APP, "app", "other-parent", "child",
                MerchantAction.CREATE_ORDER, from, relation));
        assertFalse(grant.allows(PrincipalType.MERCHANT_APP, "app", "parent", "child",
                MerchantAction.CREATE_ORDER, from, relation.revoke()));
        assertFalse(grant.revoke().allows(PrincipalType.MERCHANT_APP, "app", "parent", "child",
                MerchantAction.CREATE_ORDER, from, relation));
        assertThrows(DomainViolation.class, () -> grant.revoke().activate());
        assertThrows(DomainViolation.class, () -> relation.revoke().activate());
    }
}
