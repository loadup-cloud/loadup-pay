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
package io.github.loadup.pay.limitcenter.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.domain.model.LimitReleaseEvidence;
import io.github.loadup.pay.limitcenter.domain.model.LimitSettlementReceipt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface LimitSettlementConverter {
    LimitSettlementReceipt toReceipt(String id, String operationId, String paymentId, String kind,
                                     String businessEventId, String requestDigest, long paymentVersion, String evidenceId);

    @Mapping(target = "fenced", constant = "true")
    @Mapping(target = "reservation", source = "reservation")
    LimitReleaseDTO toRelease(String operationId, String paymentId, LimitReservationDTO reservation,
                              long paymentVersion, String noCollectionEvidenceId);

    LimitReleaseEvidence toReleaseEvidence(String paymentId, io.github.loadup.commons.money.Money amount,
                                           long paymentVersion, String evidenceId, boolean executionFenced);
}
