/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.customer.infrastructure.dataobject;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("merchant_action_grant")
public record MerchantActionGrantRow(
        @Id @Column("id") String id,
        @Column("tenant_id") String tenantId,
        @Column("created_at") LocalDateTime createdAt,
        @Column("updated_at") LocalDateTime updatedAt,
        @Column("deleted") boolean deleted,
        @Column("principal_type") String principalType,
        @Column("principal_id") String principalId,
        @Column("payee_merchant_id") String payeeMerchantId,
        @Column("relationship_id") String relationshipId,
        @Column("action") String action,
        @Column("state") String state,
        @Column("valid_from") LocalDateTime validFrom,
        @Column("valid_to") LocalDateTime validTo,
        @Column("row_version") long rowVersion) {}
