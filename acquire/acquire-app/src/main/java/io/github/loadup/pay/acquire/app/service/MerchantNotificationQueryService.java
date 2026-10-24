/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.acquire.app.converter.MerchantNotificationQueryConverter;
import io.github.loadup.pay.acquire.app.security.MerchantNotificationAccess;
import io.github.loadup.pay.acquire.client.dto.MerchantNotificationDTO;
import io.github.loadup.pay.acquire.client.facade.MerchantNotificationQueryFacade;
import io.github.loadup.pay.acquire.client.query.MerchantNotificationPageQuery;
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationGateway;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;

@Service
public class MerchantNotificationQueryService implements MerchantNotificationQueryFacade {
    private final MerchantNotificationAccess access;
    private final MerchantNotificationGateway deliveries;
    private final MerchantNotificationQueryConverter converter;
    private final OrderCreationTransactions transactions;
    private final Validator validator;
    public MerchantNotificationQueryService(MerchantNotificationAccess access, MerchantNotificationGateway deliveries,
            MerchantNotificationQueryConverter converter, OrderCreationTransactions transactions, Validator validator) {
        this.access = access; this.deliveries = deliveries; this.converter = converter; this.transactions = transactions; this.validator = validator;
    }
    @Override public MerchantNotificationDTO detail(IdQuery query) {
        validate(query);
        var actor = access.reader();
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var delivery = deliveries.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            if (actor.merchant() && !actor.merchantId().equals(delivery.payeeMerchantId())) throw new CommonException(NOT_FOUND);
            return converter.toDTO(delivery);
        });
    }
    @Override public PageDTO<MerchantNotificationDTO> page(MerchantNotificationPageQuery query) {
        validate(query);
        var actor = access.reader();
        if (actor.merchant() && !actor.merchantId().equals(query.payeeMerchantId())) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.read(actor.tenantId(), () -> PageDTO.of(deliveries.page(actor.tenantId(), converter.toFilter(query)).map(converter::toDTO)));
    }
    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }
}
