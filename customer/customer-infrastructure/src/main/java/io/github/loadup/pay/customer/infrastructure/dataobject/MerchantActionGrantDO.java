/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.customer.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("merchant_action_grant")
public class MerchantActionGrantDO extends BaseDO {
    private String principalType;
    private String principalId;
    private String payeeMerchantId;
    private String relationshipId;
    private String action;
    private String state;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private long rowVersion;

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String value) {
        principalType = value;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String value) {
        principalId = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(String value) {
        relationshipId = value;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String value) {
        action = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime value) {
        validFrom = value;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime value) {
        validTo = value;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(long value) {
        rowVersion = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
