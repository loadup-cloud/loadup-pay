/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.customer.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("merchant_relationship")
public class MerchantRelationshipDO extends BaseDO {
    private String parentMerchantId;
    private String childMerchantId;
    private String relationType;
    private String state;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private long rowVersion;

    public String getParentMerchantId() {
        return parentMerchantId;
    }

    public void setParentMerchantId(String value) {
        parentMerchantId = value;
    }

    public String getChildMerchantId() {
        return childMerchantId;
    }

    public void setChildMerchantId(String value) {
        childMerchantId = value;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String value) {
        relationType = value;
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
