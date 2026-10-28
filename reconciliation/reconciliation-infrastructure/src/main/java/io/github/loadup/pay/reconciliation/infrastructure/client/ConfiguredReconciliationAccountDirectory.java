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
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationAccountsProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;

/**
 * Closed local catalog; no credential file, remote lookup or request-supplied account hash.
 */
@Component
public class ConfiguredReconciliationAccountDirectory implements ReconciliationAccountDirectory {
    private final ReconciliationAccountsProperties properties;

    public ConfiguredReconciliationAccountDirectory(ReconciliationAccountsProperties properties) {
        this.properties = properties;
    }

    @Override
    public ReconciliationScope resolve(String tenant, String caller, String code, LocalDate date) {
        var account = account(tenant, caller, code);
        return new ReconciliationScope(account.channel(), account.accountScopeHash(), date, account.statementProfile());
    }

    @Override
    public void authorize(String tenant, String caller, String code, ReconciliationScope original) {
        var account = account(tenant, caller, code);
        // A parser/profile change does not rewrite old sealed inputs. Account identity and caller grants still apply.
        if (original == null || account.channel() != original.channel() || !account.accountScopeHash().equals(original.accountScopeHash()))
            throw new CommonException(ACTION_FORBIDDEN);
    }

    @Override
    public ReconciliationStatementAccount statementAccount(String tenant, String caller, String code, ReconciliationScope original) {
        authorize(tenant, caller, code, original);
        return account(tenant, caller, code).statementAccount();
    }

    @Override
    public String statementBindingRevision(String tenant, String caller, String code, ReconciliationScope original) {
        authorize(tenant, caller, code, original);
        var revision = account(tenant, caller, code).statementBindingRevisionId();
        if (revision == null) throw new CommonException(ACTION_FORBIDDEN);
        return revision;
    }

    private ReconciliationAccountsProperties.Account account(String tenant, String caller, String code) {
        var account = properties.tenants().getOrDefault(tenant, Map.of()).get(code);
        if (account == null || !account.serviceIds().contains(caller)) throw new CommonException(ACTION_FORBIDDEN);
        return account;
    }
}
