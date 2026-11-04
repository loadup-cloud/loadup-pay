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
package io.github.loadup.authserver;

import java.util.List;
import java.util.Set;

public record ServiceClientCatalog(List<Client> clients) {
    public ServiceClientCatalog {
        if (clients == null || clients.isEmpty()) {
            throw new IllegalArgumentException("Service clients are required");
        }
        clients = List.copyOf(clients);
        if (clients.stream().map(Client::clientId).distinct().count() != clients.size()) {
            throw new IllegalArgumentException("Duplicate service client id");
        }
    }

    public record Client(
            String clientId,
            String tenantId,
            String serviceId,
            String audience,
            Set<String> permissions,
            String secretEnv,
            boolean enabled) {
        public Client {
            requireId(clientId, 100);
            requireId(tenantId, 64);
            requireId(serviceId, 64);
            requireId(audience, 128);
            if (secretEnv == null
                    || !secretEnv.matches("[A-Z][A-Z0-9_]*")
                    || permissions == null
                    || permissions.isEmpty()
                    || permissions.stream().anyMatch(p -> p == null || !p.matches("pay:[a-z0-9:-]{1,120}"))) {
                throw new IllegalArgumentException("Invalid service client authorization");
            }
            permissions = Set.copyOf(permissions);
        }

        private static void requireId(String value, int limit) {
            if (value == null || value.isBlank() || value.length() > limit || !value.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid service client identity");
            }
        }
    }
}
