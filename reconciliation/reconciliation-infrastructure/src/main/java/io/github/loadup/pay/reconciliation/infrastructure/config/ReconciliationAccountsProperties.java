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
package io.github.loadup.pay.reconciliation.infrastructure.config;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

@ConfigurationProperties("loadup.modules.reconciliation.accounts")
public record ReconciliationAccountsProperties(Map<String, Map<String, Account>> tenants) {
    public ReconciliationAccountsProperties {
        tenants = tenants == null ? Map.of() : tenants.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        for (var tenant : tenants.entrySet()) {
            DomainChecks.id(tenant.getKey());
            for (var account : tenant.getValue().entrySet()) DomainChecks.id(account.getKey());
        }
    }
    public record Account(Channel channel, String accountScopeHash, String statementProfile, Set<String> serviceIds) {
        public Account {
            require(channel == Channel.ALIPAY || channel == Channel.WECHAT, INVALID_ARGUMENT);
            require(accountScopeHash != null && accountScopeHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
            DomainChecks.text(statementProfile, 64);
            serviceIds = serviceIds == null ? Set.of() : Set.copyOf(serviceIds);
            serviceIds.forEach(DomainChecks::id);
        }
        @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
