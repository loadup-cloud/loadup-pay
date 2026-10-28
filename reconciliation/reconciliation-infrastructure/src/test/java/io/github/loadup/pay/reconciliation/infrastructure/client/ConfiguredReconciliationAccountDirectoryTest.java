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
package io.github.loadup.pay.reconciliation.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationAccountsProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfiguredReconciliationAccountDirectoryTest {
    private final LocalDate date = LocalDate.of(2026, 10, 26);

    @Test
    void emptyCatalogAndMissingTenantCallerOrCodeDenyByDefault() {
        denied(new ConfiguredReconciliationAccountDirectory(new ReconciliationAccountsProperties(null)), "tenant", "ops", "account");
        var directory = directory("a".repeat(64), "profile");
        denied(directory, "other-tenant", "ops", "account");
        denied(directory, "tenant", "other-service", "account");
        denied(directory, "tenant", "ops", "other-account");
    }

    @Test
    void profileChangeDoesNotReinterpretOldScopeButAccountReplacementAndRevocationStillDeny() {
        var original = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), date, "profile-v1");
        var current = directory("a".repeat(64), "profile-v2");
        assertEquals("profile-v2", current.resolve("tenant", "ops", "account", date).statementProfile());
        assertDoesNotThrow(() -> current.authorize("tenant", "ops", "account", original));
        assertThrows(CommonException.class, () -> directory("b".repeat(64), "profile-v2").authorize("tenant", "ops", "account", original));
        assertThrows(CommonException.class, () -> current.authorize("tenant", "revoked", "account", original));
    }

    @Test
    void originalRevisionIsOnlyReturnedForAuthorizedOfficialSourceAccounts() {
        var current = directory("a".repeat(64), "profile");
        var scope = current.resolve("tenant", "ops", "account", date);
        assertEquals("revision", current.statementBindingRevision("tenant", "ops", "account", scope));
        assertThrows(CommonException.class, () -> current.statementBindingRevision("tenant", "other", "account", scope));
        var manualOnly = new ConfiguredReconciliationAccountDirectory(new ReconciliationAccountsProperties(Map.of("tenant", Map.of("account",
                new ReconciliationAccountsProperties.Account(Channel.WECHAT, "a".repeat(64), "profile", new io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount("1234567890", null), null, Set.of("ops"))))));
        assertDoesNotThrow(() -> manualOnly.authorize("tenant", "ops", "account", scope));
        assertThrows(CommonException.class, () -> manualOnly.statementBindingRevision("tenant", "ops", "account", scope));
    }

    private ConfiguredReconciliationAccountDirectory directory(String hash, String profile) {
        return new ConfiguredReconciliationAccountDirectory(new ReconciliationAccountsProperties(Map.of("tenant", Map.of("account",
                new ReconciliationAccountsProperties.Account(Channel.WECHAT, hash, profile, new io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount("1234567890", null), "revision", Set.of("ops"))))));
    }

    private void denied(ConfiguredReconciliationAccountDirectory directory, String tenant, String caller, String code) {
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> directory.resolve(tenant, caller, code, date)).getResultCode());
    }
}
