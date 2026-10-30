/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.customer.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.customer.domain.model.MerchantAction;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantActionGrantDO;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantRelationshipDO;
import io.github.loadup.pay.customer.infrastructure.mapper.MerchantActionGrantMapper;
import io.github.loadup.pay.customer.infrastructure.mapper.MerchantRelationshipMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantAuthorizationGatewayImplTest {
    private final MerchantRelationshipMapper relationships = mock(MerchantRelationshipMapper.class);
    private final MerchantActionGrantMapper grants = mock(MerchantActionGrantMapper.class);

    private static MerchantRelationshipDO relationship(LocalDateTime from) {
        var row = new MerchantRelationshipDO();
        row.setId("relationship");
        row.setParentMerchantId("parent");
        row.setChildMerchantId("child");
        row.setRelationType("MANAGEMENT");
        row.setState("ACTIVE");
        row.setValidFrom(from);
        row.setRowVersion(1);
        return row;
    }

    private static MerchantActionGrantDO grant(LocalDateTime from) {
        var row = new MerchantActionGrantDO();
        row.setId("grant");
        row.setPrincipalType("MERCHANT");
        row.setPrincipalId("parent");
        row.setPayeeMerchantId("child");
        row.setRelationshipId("relationship");
        row.setAction("CREATE_ORDER");
        row.setState("ACTIVE");
        row.setValidFrom(from);
        row.setRowVersion(1);
        return row;
    }

    @Test
    void rejectsReadWithDifferentTenantBeforeExecutingSql() {
        var jdbc = mock(JdbcTemplate.class);
        var gateway = new MerchantAuthorizationGatewayImpl(jdbc, relationships, grants, Clock.systemUTC());
        TenantUtil.runWithTenant("tenant-a", () ->
                assertThrows(CommonException.class, () -> gateway.findRelationship("tenant-b", "relationship")));
        verifyNoInteractions(jdbc);
        verifyNoInteractions(relationships);
        verifyNoInteractions(grants);
    }

    @Test
    void mapsRelationshipAndGrantRowsToDomainModels() {
        var gateway = new MerchantAuthorizationGatewayImpl(
                mock(JdbcTemplate.class), relationships, grants, Clock.systemUTC());
        var from = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(relationships.selectOneById("relationship")).thenReturn(relationship(from));
        when(grants.selectOneByQuery(any(QueryWrapper.class))).thenReturn(grant(from));
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
        var gateway = new MerchantAuthorizationGatewayImpl(jdbc, relationships, grants, Clock.systemUTC());
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
        var gateway = new MerchantAuthorizationGatewayImpl(jdbc, relationships, grants, Clock.systemUTC());
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
