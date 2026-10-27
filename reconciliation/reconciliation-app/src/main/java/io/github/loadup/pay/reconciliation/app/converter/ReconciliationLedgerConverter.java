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
package io.github.loadup.pay.reconciliation.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationLedgerSourceDTO;
import io.github.loadup.pay.reconciliation.domain.model.*;
import org.mapstruct.*;

@Mapper(config = LoadUpMapStructConfig.class, imports = Money.class)
public interface ReconciliationLedgerConverter {
    @Mapping(target = "channel", source = "scope.channel")
    @Mapping(target = "accountScopeHash", source = "scope.accountScopeHash")
    @Mapping(target = "billDate", source = "scope.billDate")
    @Mapping(target = "statementProfile", source = "scope.statementProfile")
    @Mapping(target = "requestKey", source = "requestKey")
    PaymentCaptureCreateCommand toCreate(ReconciliationScope scope, String requestKey);
    ReconciliationLedgerSourceDTO toDTO(PaymentCaptureDTO value);
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "channelTransactionNo", source = "channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "channelRefundNo")
    ReconciliationKey toKey(PaymentLedgerLineDTO value);
    @Mapping(target = "key", source = "value")
    @Mapping(target = "amount", expression = "java(Money.ofMinor(value.amountMinor(), value.currency()))")
    @Mapping(target = "recordNumber", source = "ordinal")
    ReconciliationLine toLine(PaymentLedgerLineDTO value);
}
