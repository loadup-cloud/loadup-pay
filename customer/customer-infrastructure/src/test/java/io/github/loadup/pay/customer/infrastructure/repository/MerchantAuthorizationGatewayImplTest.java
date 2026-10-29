/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.customer.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MerchantAuthorizationGatewayImplTest {
    @Test
    void rejectsReadWithDifferentTenantBeforeExecutingSql() {
        var jdbc = mock(JdbcTemplate.class);
        var gateway = new MerchantAuthorizationGatewayImpl(jdbc, Clock.systemUTC());
        TenantUtil.runWithTenant("tenant-a", () ->
                assertThrows(CommonException.class, () -> gateway.findRelationship("tenant-b", "relationship")));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsLockWithoutTransactionBeforeExecutingSql() {
        var jdbc = mock(JdbcTemplate.class);
        var gateway = new MerchantAuthorizationGatewayImpl(jdbc, Clock.systemUTC());
        TenantUtil.runWithTenant("tenant-a", () ->
                assertThrows(CommonException.class, () -> gateway.lockRelationship("tenant-a", "relationship")));
        verifyNoInteractions(jdbc);
    }
}
