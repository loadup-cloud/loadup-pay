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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record MerchantContract(
        String merchantId,
        String scopeKey,
        String contractId,
        ContractStatus status,
        long generation,
        List<MerchantContractRevision> revisions) {
    public MerchantContract {
        merchantId = ContractChecks.code(merchantId);
        scopeKey = ContractChecks.code(scopeKey);
        contractId = ContractChecks.code(contractId);
        Objects.requireNonNull(status, "status");
        if (generation < 1) {
            throw new CommonException(ContractResultCodeEnum.GENERATION_MUST_BE_POSITIVE);
        }
        revisions = revisions.stream()
                .sorted(Comparator.comparing(MerchantContractRevision::effectiveFrom))
                .toList();
        if (revisions.isEmpty() || revisions.size() > 256) {
            throw new CommonException(ContractResultCodeEnum.INVALID_REVISION_COUNT);
        }
        var versions = new HashSet<Integer>();
        MerchantContractRevision previous = null;
        for (var revision : revisions) {
            if (!revision.merchantId().equals(merchantId)
                    || !revision.scopeKey().equals(scopeKey)
                    || !revision.contractId().equals(contractId)) {
                throw new CommonException(ContractResultCodeEnum.CONTRACT_IDENTITY_MISMATCH);
            }
            if (!versions.add(revision.revision())) {
                throw new CommonException(ContractResultCodeEnum.DUPLICATE_REVISION);
            }
            if (previous != null
                    && (previous.effectiveTo() == null
                            || revision.effectiveFrom().isBefore(previous.effectiveTo()))) {
                throw new CommonException(ContractResultCodeEnum.OVERLAPPING_REVISIONS);
            }
            previous = revision;
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
