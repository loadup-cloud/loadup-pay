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
package io.github.loadup.pay.customer.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.globalunique.GlobalUniqueTemplate;
import io.github.loadup.components.globalunique.model.GlobalUniqueClaim;
import io.github.loadup.modules.merchant.client.facade.MerchantQueryFacade;
import io.github.loadup.pay.customer.app.converter.CustomerConverter;
import io.github.loadup.pay.customer.app.security.CustomerActor;
import io.github.loadup.pay.customer.app.security.CustomerActorProvider;
import io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.client.command.MerchantRelationshipRevokeCommand;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.domain.gateway.MerchantAuthorizationGateway;
import io.github.loadup.pay.customer.domain.gateway.MerchantFactsGateway;
import io.github.loadup.pay.customer.domain.model.MerchantAction;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantFacts;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerServiceTest {
    private final CustomerActorProvider actors = mock(CustomerActorProvider.class);
    private final MerchantFactsGateway facts = mock(MerchantFactsGateway.class);
    private final MerchantAuthorizationGateway authorization = mock(MerchantAuthorizationGateway.class);
    private final MerchantQueryFacade merchants = mock(MerchantQueryFacade.class);
    private final GlobalUniqueTemplate unique = mock(GlobalUniqueTemplate.class);
    private final CustomerConverter converter = mock(CustomerConverter.class);
    private final IdGenerator ids = () -> "generated";
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0);
    private final CustomerService service = new CustomerService(
            actors,
            facts,
            authorization,
            merchants,
            new CustomerCommandExecutor(unique),
            converter,
            ids,
            Clock.fixed(now.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()),
            validation.getValidator());

    CustomerServiceTest() {
        when(actors.current())
                .thenReturn(new CustomerActor("tenant", PrincipalType.MERCHANT_APP, "app", "parent", false));
        when(facts.lock("tenant", "child")).thenReturn(new MerchantFacts("child", true, 2));
    }

    @AfterEach
    void closeValidator() {
        validation.close();
    }

    private MerchantActionAuthorizeCommand query() {
        return new MerchantActionAuthorizeCommand(
                "child", io.github.loadup.pay.customer.client.enums.MerchantAction.CREATE_ORDER);
    }

    @Test
    void relationshipAloneDoesNotAuthorizeOrExposeProfile() {
        service.authorize(query());
        verify(converter).toEligibility(false, CustomerResultCodeEnum.ACTION_FORBIDDEN.getCode(), null, null, null);
        verifyNoInteractions(merchants);
    }

    @Test
    void revokedRelationshipInvalidatesOtherwiseActiveGrant() {
        var relationship = MerchantRelationship.init("relation", "parent", "child", now.minusDays(1), null)
                .activate()
                .revoke();
        var grant = MerchantActionGrant.init(
                        "grant",
                        PrincipalType.MERCHANT_APP,
                        "app",
                        "child",
                        "relation",
                        MerchantAction.CREATE_ORDER,
                        now.minusDays(1),
                        null)
                .activate();
        when(authorization.findGrant("tenant", PrincipalType.MERCHANT_APP, "app", "child", MerchantAction.CREATE_ORDER))
                .thenReturn(Optional.of(grant));
        when(authorization.findRelationship("tenant", "relation")).thenReturn(Optional.of(relationship));
        service.authorize(query());
        verify(converter).toEligibility(false, CustomerResultCodeEnum.ACTION_FORBIDDEN.getCode(), null, null, null);
        verifyNoInteractions(merchants);
    }

    @Test
    void explicitGrantReturnsOnlyTheFrameworkSafeProfile() {
        var grant = MerchantActionGrant.init(
                        "grant",
                        PrincipalType.MERCHANT_APP,
                        "app",
                        "child",
                        null,
                        MerchantAction.CREATE_ORDER,
                        now.minusDays(1),
                        null)
                .activate();
        var profile = new io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO(
                "child", "tenant", "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true);
        when(authorization.findGrant("tenant", PrincipalType.MERCHANT_APP, "app", "child", MerchantAction.CREATE_ORDER))
                .thenReturn(Optional.of(grant));
        when(facts.find("tenant", "parent")).thenReturn(Optional.of(new MerchantFacts("parent", true, 1)));
        when(merchants.find("tenant", "child")).thenReturn(Optional.of(profile));
        service.authorize(query());
        verify(converter).toEligibility(true, "ALLOWED", profile, new MerchantFacts("child", true, 2), grant);
    }

    @Test
    void validToIsExclusiveAndDoesNotExposeProfile() {
        var grant = MerchantActionGrant.init(
                        "grant",
                        PrincipalType.MERCHANT_APP,
                        "app",
                        "child",
                        null,
                        MerchantAction.CREATE_ORDER,
                        now.minusDays(1),
                        now)
                .activate();
        when(authorization.findGrant("tenant", PrincipalType.MERCHANT_APP, "app", "child", MerchantAction.CREATE_ORDER))
                .thenReturn(Optional.of(grant));
        service.authorize(query());
        verify(converter).toEligibility(false, CustomerResultCodeEnum.ACTION_FORBIDDEN.getCode(), null, null, null);
        verifyNoInteractions(merchants);
    }

    @Test
    void trustedTenantOverridesAndThenRestoresAmbientTenant() {
        when(facts.lock("tenant", "child")).thenAnswer(call -> {
            assertEquals("tenant", TenantUtil.getTenantId());
            return new MerchantFacts("child", false, 2);
        });
        TenantUtil.runWithTenant("untrusted-header", () -> {
            service.authorize(query());
            assertEquals("untrusted-header", TenantUtil.getTenantId());
        });
        verify(converter).toEligibility(false, CustomerResultCodeEnum.MERCHANT_INACTIVE.getCode(), null, null, null);
    }

    @Test
    void mutationRequiresSeparateManagementPermission() {
        var command = new MerchantRelationshipRevokeCommand("relation", "request", 1);
        CommonException failure = assertThrows(CommonException.class, () -> service.revokeRelationship(command));
        assertSame(CustomerResultCodeEnum.ACTION_FORBIDDEN, failure.getResultCode());
        verifyNoInteractions(unique, facts, authorization);
    }

    @Test
    void staleRevokeVersionDoesNotClaimOrWrite() {
        when(actors.current()).thenReturn(new CustomerActor("tenant", PrincipalType.OPERATOR, "admin", null, true));
        var relation = MerchantRelationship.init("relation", "parent", "child", now.minusDays(1), null)
                .activate();
        when(authorization.findRelationship("tenant", "relation")).thenReturn(Optional.of(relation));
        when(authorization.lockRelationship("tenant", "relation")).thenReturn(relation);
        CommonException failure = assertThrows(
                CommonException.class,
                () -> service.revokeRelationship(new MerchantRelationshipRevokeCommand("relation", "request", 0)));
        assertSame(CustomerResultCodeEnum.CONFLICT, failure.getResultCode());
        verify(unique, never()).claim(any(GlobalUniqueClaim.class));
        verify(authorization, never()).saveRelationship(anyString(), any(), anyLong());
    }

    @Test
    void facadeValidatesInputEvenWithoutAnHttpController() {
        CommonException failure = assertThrows(
                CommonException.class, () -> service.authorize(new MerchantActionAuthorizeCommand("", null)));
        assertSame(CustomerResultCodeEnum.INVALID_ARGUMENT, failure.getResultCode());
        verifyNoInteractions(actors, facts);
    }
}
