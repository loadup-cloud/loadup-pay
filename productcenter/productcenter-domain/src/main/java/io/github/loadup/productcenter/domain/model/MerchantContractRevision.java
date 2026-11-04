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
package io.github.loadup.productcenter.domain.model;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MerchantContractRevision(
        String merchantId,
        String scopeKey,
        String contractId,
        int revision,
        String planCode,
        int planVersion,
        Instant signedAt,
        Instant effectiveFrom,
        Instant effectiveTo,
        Map<String, ResolvedContractItem> items) {
    public MerchantContractRevision {
        merchantId = ContractChecks.code(merchantId);
        scopeKey = ContractChecks.code(scopeKey);
        contractId = ContractChecks.code(contractId);
        revision = ContractChecks.version(revision);
        planCode = ContractChecks.code(planCode);
        planVersion = ContractChecks.version(planVersion);
        Objects.requireNonNull(signedAt, "signedAt");
        ContractChecks.interval(effectiveFrom, effectiveTo);
        if (effectiveFrom.isBefore(signedAt)) {
            throw new CommonException(ContractResultCodeEnum.BACKDATED_ACTIVATION_UNSUPPORTED);
        }
        items = ContractChecks.map(items);
        if (items.isEmpty()) {
            throw new CommonException(ContractResultCodeEnum.EMPTY_SIGNED_CONTRACT);
        }
    }

    public boolean effectiveAt(Instant time) {
        return !time.isBefore(effectiveFrom) && (effectiveTo == null || time.isBefore(effectiveTo));
    }

    public String snapshotHash() {
        return SnapshotDigest.hash(this);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
