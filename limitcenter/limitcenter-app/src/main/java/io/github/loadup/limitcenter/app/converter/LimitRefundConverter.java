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
package io.github.loadup.limitcenter.app.converter;

import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.limitcenter.domain.model.LimitRefundReceipt;
import java.time.LocalDateTime;
import org.mapstruct.Mapper;

@Mapper(config = LoadUpMapStructConfig.class)
public interface LimitRefundConverter {
    LimitRefundReceipt toReceipt(
            String id,
            String callerServiceId,
            LimitRefundRestoreCommand command,
            String requestDigest,
            long verifiedPaymentRefundedMinor,
            long verifiedPaymentRefundVersion,
            String result,
            long restoredAmountMinor,
            boolean restoredPaymentCount,
            LocalDateTime acceptedAt);

    LimitRestoreDTO toDTO(LimitRefundReceipt value);
}
