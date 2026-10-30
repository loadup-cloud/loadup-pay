/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.customer.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.customer.domain.model.MerchantAction;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantActionGrantRow;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantRelationshipRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.relational.core.query.Query;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MerchantAuthorizationGatewayImplTest {
    @Test
    void rejectsReadWithDifferentTenantBeforeExecutingSql() {
        var jdbc = mock(JdbcTemplate.class);
        var aggregates = mock(JdbcAggregateTemplate.class);
        var gateway = new MerchantAuthorizationGatewayImpl(jdbc, aggregates, Clock.systemUTC());
        TenantUtil.runWithTenant("tenant-a", () ->
                assertThrows(CommonException.class, () -> gateway.findRelationship("tenant-b", "relationship")));
        verifyNoInteractions(jdbc);
        verifyNoInteractions(aggregates);
    }

    @Test
    void mapsRelationshipAndGrantRowsToDomainModels() {
        var aggregates = mock(JdbcAggregateTemplate.class);
        var gateway = new MerchantAuthorizationGatewayImpl(mock(JdbcTemplate.class), aggregates, Clock.systemUTC());
        var from = LocalDateTime.of(2026, 1, 1, 0, 0);
        var relationship = new MerchantRelationshipRow("relationship", "tenant-a", from, from, false,
                "parent", "child", "MANAGEMENT", "ACTIVE", from, null, 1);
        var grant = new MerchantActionGrantRow("grant", "tenant-a", from, from, false, "MERCHANT", "parent",
                "child", "relationship", "CREATE_ORDER", "ACTIVE", from, null, 1);
        when(aggregates.findOne(any(Query.class), eq(MerchantRelationshipRow.class))).thenReturn(Optional.of(relationship));
        when(aggregates.findOne(any(Query.class), eq(MerchantActionGrantRow.class))).thenReturn(Optional.of(grant));
        TenantUtil.runWithTenant("tenant-a", () -> {
            assertEquals(MerchantRelationship.init("relationship", "parent", "child", from, null).activate(),
                    gateway.findRelationship("tenant-a", "relationship").orElseThrow());
            assertEquals(MerchantActionGrant.init("grant", PrincipalType.MERCHANT, "parent", "child",
                            "relationship", MerchantAction.CREATE_ORDER, from, null).activate(),
                    gateway.findGrant("tenant-a", PrincipalType.MERCHANT, "parent", "child", MerchantAction.CREATE_ORDER)
                            .orElseThrow());
        });
    }

    @Test
    void rejectsLockWithoutTransactionBeforeExecutingSql() {
        var jdbc = mock(JdbcTemplate.class);
        var gateway = new MerchantAuthorizationGatewayImpl(jdbc, mock(org.springframework.data.jdbc.core.JdbcAggregateTemplate.class), Clock.systemUTC());
        TenantUtil.runWithTenant("tenant-a", () ->
                assertThrows(CommonException.class, () -> gateway.lockRelationship("tenant-a", "relationship")));
        verifyNoInteractions(jdbc);
    }

    @Test
    void acceptsNewActivatedRelationshipAndGrant() {
        var source = mock(DataSource.class);
        var jdbc = mock(JdbcTemplate.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getDataSource" -> source;
            case "update" -> 1;
            default -> org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        var gateway = new MerchantAuthorizationGatewayImpl(jdbc, mock(org.springframework.data.jdbc.core.JdbcAggregateTemplate.class), Clock.systemUTC());
        var from = LocalDateTime.of(2026, 1, 1, 0, 0);
        var relationship = MerchantRelationship.init("relationship", "parent", "child", from, null).activate();
        var grant = MerchantActionGrant.init("grant", PrincipalType.MERCHANT, "parent", "child",
                "relationship", MerchantAction.CREATE_ORDER, from, null).activate();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(source, new Object());
        try {
            TenantUtil.runWithTenant("tenant-a", () -> {
                gateway.saveRelationship("tenant-a", relationship, -1);
                gateway.saveGrant("tenant-a", grant, -1);
                gateway.saveRelationship("tenant-a", relationship.revoke(), relationship.version());
                gateway.saveGrant("tenant-a", grant.revoke(), grant.version());
                assertThrows(CommonException.class, () -> gateway.saveGrant("tenant-a", grant.revoke(), 0));
            });
        } finally {
            TransactionSynchronizationManager.unbindResource(source);
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
