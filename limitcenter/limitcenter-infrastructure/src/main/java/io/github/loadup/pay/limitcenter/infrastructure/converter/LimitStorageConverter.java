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
package io.github.loadup.pay.limitcenter.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitExecutionGrant;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitRule;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.WindowBounds;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitBucketDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitExecutionGrantDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitOperationGateDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyHeadDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyRevisionDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitReservationDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitReservationItemDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitRuleRuntimeDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.DayOfWeek;

@Mapper(config = LoadUpMapStructConfig.class)
public interface LimitStorageConverter {
    // MapStruct 1.6 treats single-argument methods returning the record type as fluent setters.
    // Exclude domain transitions explicitly; keep strict validation of real target fields.
    @Mapping(target = "advance", ignore = true)
    LimitPolicyHead toHead(LimitPolicyHeadDO row);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    LimitPolicyHeadDO toHeadDO(String tenant, LimitPolicyHead value);

    @Mapping(target = "rule", source = "row")
    RuntimeLimitRule toRuntime(LimitRuleRuntimeDO row);

    @Mapping(target = "window", source = "windowType")
    @Mapping(target = "threshold", source = "currentLimit")
    LimitRule toRule(LimitRuleRuntimeDO row);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "ownerPolicyId", source = "value.ownerPolicyId")
    @Mapping(target = "policyVersion", source = "value.policyVersion")
    @Mapping(target = "ruleScopeId", source = "value.rule.ruleScopeId")
    @Mapping(target = "merchantId", source = "value.rule.merchantId")
    @Mapping(target = "metric", source = "value.rule.metric")
    @Mapping(target = "currency", source = "value.rule.currency")
    @Mapping(target = "windowType", source = "value.rule.window")
    @Mapping(target = "weekStart", source = "value.rule.weekStart")
    @Mapping(target = "currentLimit", source = "value.rule.threshold")
    @Mapping(target = "enforcementEnabled", source = "value.rule.enforcementEnabled")
    @Mapping(target = "meteringEnabled", source = "value.rule.meteringEnabled")
    @Mapping(target = "timezone", expression = "java(java.time.ZoneId.systemDefault().getId())")
    LimitRuleRuntimeDO toRuntimeDO(String tenant, RuntimeLimitRule value);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "compiledRules", source = "payload")
    LimitPolicyRevisionDO toRevisionDO(String tenant, ActivatedLimitPolicy value, String payload);

    @Mapping(target = "key", source = "row")
    @Mapping(target = "used", source = "usedValue")
    @Mapping(target = "held", source = "heldValue")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "confirm", ignore = true)
    @Mapping(target = "release", ignore = true)
    @Mapping(target = "restore", ignore = true)
    LimitBucket toBucket(LimitBucketDO row);

    @Mapping(target = "window", source = "windowType")
    @Mapping(target = "bounds", source = "row")
    LimitBucketKey toKey(LimitBucketDO row);

    @Mapping(target = "start", source = "windowStart")
    @Mapping(target = "end", source = "windowEnd")
    WindowBounds toBounds(LimitBucketDO row);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "ruleScopeId", source = "value.key.ruleScopeId")
    @Mapping(target = "merchantId", source = "value.key.merchantId")
    @Mapping(target = "metric", source = "value.key.metric")
    @Mapping(target = "currency", source = "value.key.currency")
    @Mapping(target = "windowType", source = "value.key.window")
    @Mapping(target = "windowStart", source = "value.key.bounds.start")
    @Mapping(target = "windowEnd", source = "value.key.bounds.end")
    @Mapping(target = "usedValue", source = "value.used")
    @Mapping(target = "heldValue", source = "value.held")
    @Mapping(target = "rowVersion", source = "value.version")
    LimitBucketDO toBucketDO(String tenant, LimitBucket value);

    LimitOperationGate toGate(LimitOperationGateDO row);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "rowVersion", source = "rowVersion")
    LimitOperationGateDO toGateDO(String tenant, LimitOperationGate gate, long rowVersion);

    @Mapping(target = "reservation", source = "row")
    @Mapping(target = "transitionTo", ignore = true)
    StoredLimitReservation toStored(LimitReservationDO row);

    @Mapping(
            target = "amount",
            expression =
                    "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "release", ignore = true)
    @Mapping(target = "expire", ignore = true)
    LimitReservation toReservation(LimitReservationDO row);

    @Mapping(target = "id", source = "stored.reservation.id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "operationId", source = "stored.reservation.operationId")
    @Mapping(target = "paymentId", source = "stored.reservation.paymentId")
    @Mapping(target = "orderId", source = "stored.reservation.orderId")
    @Mapping(target = "payeeMerchantId", source = "stored.reservation.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "stored.reservation.amount.cent")
    @Mapping(target = "currency", source = "stored.reservation.amount.currencyValue")
    @Mapping(target = "policyId", source = "stored.reservation.policyId")
    @Mapping(target = "policyVersion", source = "stored.reservation.policyVersion")
    @Mapping(target = "initiatedAt", source = "stored.reservation.initiatedAt")
    @Mapping(target = "state", source = "stored.reservation.state")
    @Mapping(target = "reserveExpiresAt", source = "stored.reservation.reserveExpiresAt")
    @Mapping(target = "activeAttemptId", source = "stored.reservation.activeAttemptId")
    @Mapping(target = "executionEpoch", source = "stored.reservation.executionEpoch")
    @Mapping(target = "paymentSourceVersion", source = "stored.reservation.paymentSourceVersion")
    @Mapping(target = "rowVersion", source = "stored.reservation.version")
    LimitReservationDO toReservationDO(String tenant, StoredLimitReservation stored);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "reservedValue", source = "item.reserved")
    @Mapping(target = "consumedValue", source = "item.consumed")
    @Mapping(target = "restoredValue", source = "item.restored")
    LimitReservationItemDO toItemDO(String tenant, LimitReservationItem item, String bucketId);

    LimitExecutionGrant toExecutionGrant(LimitExecutionGrantDO row);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    LimitExecutionGrantDO toExecutionGrantDO(String tenant, LimitExecutionGrant value);

    @Mapping(target = "bucket", source = "bucket")
    @Mapping(target = "reserved", source = "row.reservedValue")
    @Mapping(target = "consumed", source = "row.consumedValue")
    @Mapping(target = "restored", source = "row.restoredValue")
    LimitReservationItem toItem(LimitReservationItemDO row, LimitBucketKey bucket);

    default DayOfWeek toDay(Integer value) {
        return DayOfWeek.of(value);
    }

    default Integer toDayNumber(DayOfWeek value) {
        return value.getValue();
    }
}
