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
package io.github.loadup.reconciliation.infrastructure.config;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.reconciliation.domain.model.ReconciliationStatementAccount;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loadup.modules.reconciliation.accounts")
public record ReconciliationAccountsProperties(Map<String, Account> accounts) {
    public ReconciliationAccountsProperties {
        accounts = accounts == null ? Map.of() : Map.copyOf(accounts);
        accounts.keySet().forEach(DomainChecks::id);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public record Account(
            Channel channel,
            String accountScopeHash,
            String statementProfile,
            ReconciliationStatementAccount statementAccount,
            String statementBindingRevisionId,
            Set<String> serviceIds) {
        public Account {
            require(channel == Channel.ALIPAY || channel == Channel.WECHAT, INVALID_ARGUMENT);
            require(accountScopeHash != null && accountScopeHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
            DomainChecks.text(statementProfile, 64);
            Objects.requireNonNull(statementAccount);
            if (statementBindingRevisionId != null) {
                DomainChecks.id(statementBindingRevisionId);
            }
            serviceIds = serviceIds == null ? Set.of() : Set.copyOf(serviceIds);
            serviceIds.forEach(DomainChecks::id);
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }
}
