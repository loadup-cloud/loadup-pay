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
package io.github.loadup.oauth2server;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.oauth.management.OAuthClientAuthorization;
import io.github.loadup.components.oauth.management.OAuthClientCreateRequest;
import io.github.loadup.components.oauth.management.OAuthClientCredentialResponse;
import io.github.loadup.components.oauth.management.OAuthClientDetails;
import io.github.loadup.components.oauth.management.OAuthClientExpiryRequest;
import io.github.loadup.components.oauth.management.OAuthClientManagement;
import io.github.loadup.components.oauth.management.OAuthClientManagementPaths;
import io.github.loadup.modules.audit.client.command.AuditRecordCommand;
import io.github.loadup.modules.audit.client.facade.AuditFacade;
import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Local mutations and their successful audit event commit together in the OAuth database. */
@Service
public class OAuthClientManagementService {
    public enum Action {
        CREATE("create"),
        READ("detail"),
        AUTHORIZE("authorization"),
        DISABLE("disable"),
        EXPIRE("expiry"),
        ROTATE("rotate");
        private final String suffix;

        Action(String suffix) {
            this.suffix = suffix;
        }
    }

    private final OAuthClientManagement clients;
    private final AuditFacade audit;
    private final TransactionTemplate write;
    private final TransactionTemplate failureWrite;

    public OAuthClientManagementService(
            OAuthClientManagement clients, AuditFacade audit, DataSourceTransactionManager transactions) {
        this.clients = clients;
        this.audit = audit;
        this.write = new TransactionTemplate(transactions);
        this.failureWrite = new TransactionTemplate(transactions);
        this.failureWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public OAuthClientCredentialResponse create(String id, OAuthClientCreateRequest request, Authentication actor) {
        return perform(Action.CREATE, id, actor, () -> clients.create(id, request));
    }

    public OAuthClientDetails detail(String id, Authentication actor) {
        return perform(Action.READ, id, actor, () -> clients.detail(id));
    }

    public OAuthClientDetails authorize(String id, OAuthClientAuthorization request, Authentication actor) {
        return perform(Action.AUTHORIZE, id, actor, () -> {
            clients.updateAuthorization(id, request);
            return clients.detail(id);
        });
    }

    public OAuthClientDetails disable(String id, Authentication actor) {
        return perform(Action.DISABLE, id, actor, () -> {
            clients.disable(id);
            return clients.detail(id);
        });
    }

    public OAuthClientDetails expire(String id, OAuthClientExpiryRequest request, Authentication actor) {
        return perform(Action.EXPIRE, id, actor, () -> {
            clients.setCredentialExpiry(id, request);
            return clients.detail(id);
        });
    }

    public OAuthClientCredentialResponse rotate(String id, OAuthClientExpiryRequest request, Authentication actor) {
        return perform(Action.ROTATE, id, actor, () -> clients.rotateCredential(id, request));
    }

    private <T> T perform(Action action, String id, Authentication authentication, Supplier<T> operation) {
        OAuthClientManagement.requireId(id);
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getCredentials() instanceof Jwt jwt)
                || !"OPERATOR".equals(jwt.getClaimAsString("principal_type"))
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()
                || jwt.getSubject().length() > 128) {
            throw new CommonException(CommonResultCodeEnum.ACCESS_DENIED);
        }
        String actor = jwt.getSubject();
        String permission = "oauth:client:"
                + switch (action) {
                    case READ -> "read";
                    case AUTHORIZE -> "authorize";
                    case EXPIRE -> "expire";
                    default -> action.name().toLowerCase(Locale.ROOT);
                };
        if (authentication.getAuthorities().stream()
                .noneMatch(authority -> permission.equals(authority.getAuthority()))) {
            throw new CommonException(CommonResultCodeEnum.ACCESS_DENIED);
        }
        try {
            return write.execute(status -> {
                T result = operation.get();
                record(action, id, actor, "SUCCESS");
                return result;
            });
        } catch (RuntimeException failure) {
            try {
                failureWrite.executeWithoutResult(status -> record(action, id, actor, "FAILURE"));
            } catch (RuntimeException unavailable) {
                // No exception body, request, response, authorization header or credential is logged.
                LoggerFactory.getLogger(OAuthClientManagementService.class)
                        .error("OAuth management failure audit could not be persisted");
            }
            if (failure instanceof CommonException known) {
                throw known;
            }
            throw new CommonException(CommonResultCodeEnum.SYS_ERROR);
        }
    }

    private void record(Action action, String id, String actor, String outcome) {
        audit.record(new AuditRecordCommand(
                actor,
                "OAUTH_CLIENT_" + action.name(),
                "POST",
                OAuthClientManagementPaths.BASE + "/" + id + "/" + action.suffix,
                outcome,
                MDC.get("traceId")));
    }
}
