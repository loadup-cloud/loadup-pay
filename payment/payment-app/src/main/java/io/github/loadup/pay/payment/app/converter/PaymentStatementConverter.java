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
package io.github.loadup.pay.payment.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.channel.api.model.VerifiedChannelStatement;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentStatementConverter {
    @Mapping(target = "channel", source = "scope.channel")
    @Mapping(target = "accountScopeHash", source = "scope.accountScopeHash")
    @Mapping(target = "billDate", source = "scope.billDate")
    @Mapping(target = "statementProfile", source = "scope.statementProfile")
    @Mapping(target = "format", source = "receipt.format")
    @Mapping(target = "requestSha256", source = "receipt.requestSha256")
    @Mapping(target = "evidenceSha256", source = "receipt.evidenceSha256")
    @Mapping(target = "officialHashType", source = "receipt.officialHashType")
    @Mapping(target = "officialHash", source = "receipt.officialHash")
    @Mapping(target = "contentSha256", source = "receipt.contentSha256")
    @Mapping(target = "receivedAt", source = "receipt.receivedAt")
    @Mapping(target = "downloadedAt", source = "receipt.downloadedAt")
    @Mapping(target = "evidence", source = "receipt.evidence")
    PaymentStatementDTO toDTO(VerifiedChannelStatement value);
}
