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
package io.github.loadup.pay.product.infrastructure.converter;

import io.github.loadup.commons.contract.ContractReference;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.product.app.model.ActivatedLimitPolicy;
import io.github.loadup.pay.product.domain.model.AdmissionIntent;
import io.github.loadup.pay.product.domain.model.OrderAdmission;
import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;
import io.github.loadup.pay.product.infrastructure.dataobject.ContractLimitPublicationDO;
import io.github.loadup.pay.product.infrastructure.dataobject.OrderAdmissionDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface AdmissionStorageConverter {
    @Mapping(target = "intent", source = "row")
    @Mapping(target = "admission", source = "row")
    StoredOrderAdmission toStored(OrderAdmissionDO row);

    @Mapping(
            target = "amount",
            expression =
                    "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    AdmissionIntent toIntent(OrderAdmissionDO row);

    @Mapping(target = "decision", source = "state")
    @Mapping(target = "contract", expression = "java(row.getContractId() == null ? null : toReference(row))")
    OrderAdmission toAdmission(OrderAdmissionDO row);

    @Mapping(target = "revision", source = "contractRevision")
    @Mapping(target = "generation", source = "contractGeneration")
    ContractReference toReference(OrderAdmissionDO row);

    ActivatedLimitPolicy toPolicy(ContractLimitPublicationDO row);

    @Mapping(target = "id", source = "value.admission.id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "orderId", source = "value.intent.orderId")
    @Mapping(target = "payeeMerchantId", source = "value.intent.payeeMerchantId")
    @Mapping(target = "productScopeKey", source = "value.intent.productScopeKey")
    @Mapping(target = "requestDigest", source = "value.intent.requestDigest")
    @Mapping(target = "amountMinor", source = "value.intent.amount.cent")
    @Mapping(target = "currency", source = "value.intent.amount.currencyValue")
    @Mapping(target = "requestedDueAt", source = "value.intent.requestedDueAt")
    @Mapping(target = "callerScopeHash", source = "value.intent.callerScopeHash")
    @Mapping(target = "contractId", source = "value.admission.contract.contractId")
    @Mapping(
            target = "contractRevision",
            expression = "java(value.admission().contract() == null ? null : value.admission().contract().revision())")
    @Mapping(
            target = "contractGeneration",
            expression =
                    "java(value.admission().contract() == null ? null : value.admission().contract().generation())")
    @Mapping(target = "merchantFactVersion", source = "value.merchantFactVersion")
    @Mapping(target = "authorizationVersion", source = "value.authorizationVersion")
    @Mapping(target = "state", source = "value.admission.decision")
    @Mapping(target = "reasonCode", source = "value.admission.reasonCode")
    @Mapping(target = "admittedAt", source = "value.admission.admittedAt")
    @Mapping(target = "materializeBefore", source = "value.admission.materializeBefore")
    @Mapping(target = "snapshotHash", source = "value.admission.contract.snapshotHash")
    @Mapping(target = "compiledTermsHash", source = "value.admission.contract.compiledTermsHash")
    @Mapping(target = "compiledTerms", source = "value.compiledTerms")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    OrderAdmissionDO toDO(String tenant, StoredOrderAdmission value);
}
