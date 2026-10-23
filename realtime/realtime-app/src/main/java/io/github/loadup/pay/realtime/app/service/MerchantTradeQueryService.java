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
package io.github.loadup.pay.realtime.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.realtime.app.converter.TradeProjectionConverter;
import io.github.loadup.pay.realtime.app.security.RealtimeActor;
import io.github.loadup.pay.realtime.app.security.RealtimeActorProvider;
import io.github.loadup.pay.realtime.client.dto.TradeDetailDTO;
import io.github.loadup.pay.realtime.client.dto.TradeOrderDTO;
import io.github.loadup.pay.realtime.client.dto.TradePaymentDTO;
import io.github.loadup.pay.realtime.client.dto.TradeRefundDTO;
import io.github.loadup.pay.realtime.client.facade.MerchantTradeQueryFacade;
import io.github.loadup.pay.realtime.client.query.TradeDetailQuery;
import io.github.loadup.pay.realtime.client.query.TradeOrderPageQuery;
import io.github.loadup.pay.realtime.client.query.TradePaymentPageQuery;
import io.github.loadup.pay.realtime.client.query.TradeRefundPageQuery;
import io.github.loadup.pay.realtime.domain.gateway.TradeQueryGateway;
import io.github.loadup.pay.realtime.domain.model.TradeOrderProjection;
import io.github.loadup.pay.realtime.domain.model.TradePageFilter;
import io.github.loadup.pay.realtime.domain.model.TradeRefundSummary;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.QUERY_FORBIDDEN;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class MerchantTradeQueryService implements MerchantTradeQueryFacade {
    private final RealtimeActorProvider actors;
    private final TradeQueryGateway queries;
    private final TradeProjectionConverter converter;
    private final RealtimeTransactions transactions;
    private final Validator validator;

    public MerchantTradeQueryService(RealtimeActorProvider actors, TradeQueryGateway queries, TradeProjectionConverter converter,
                                     RealtimeTransactions transactions, Validator validator) {
        this.actors = actors;
        this.queries = queries;
        this.converter = converter;
        this.transactions = transactions;
        this.validator = validator;
    }

    @Override
    public TradeDetailDTO detail(TradeDetailQuery query) {
        validate(query);
        var actor = actor(query.payeeMerchantId());
        return transactions.read(actor.tenantId(), () -> {
            var order = queries.findOrder(actor.tenantId(), actor.merchantId(), query.orderId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            var summary = queries.findRefundSummary(actor.tenantId(), actor.merchantId(), query.orderId()).orElse(null);
            return new TradeDetailDTO(orderDTO(order, summary), summary == null ? null : converter.toEvent(summary),
                    BusinessTime.external(order.snapshotAt()));
        });
    }

    @Override
    public PageDTO<TradeOrderDTO> page(TradeOrderPageQuery query) {
        validate(query);
        var actor = actor(query.payeeMerchantId());
        return transactions.read(actor.tenantId(), () -> {
            var page = queries.orders(actor.tenantId(), new TradePageFilter(actor.merchantId(), null, query.merchantOrderNo(), null,
                    name(query.state()), local(query.createdFrom()), local(query.createdTo()), query.page(), query.size()));
            var summaries = queries.refundSummaries(actor.tenantId(), actor.merchantId(),
                    page.records().stream().map(TradeOrderProjection::orderId).collect(Collectors.toUnmodifiableSet()));
            return PageDTO.of(page.records().stream().map(order -> orderDTO(order, summaries.get(order.orderId()))).toList(),
                    page.total(), page.page(), page.size());
        });
    }

    @Override
    public PageDTO<TradePaymentDTO> payments(TradePaymentPageQuery query) {
        validate(query);
        var actor = actor(query.payeeMerchantId());
        return transactions.read(actor.tenantId(), () -> {
            var page = queries.payments(actor.tenantId(), new TradePageFilter(actor.merchantId(), query.orderId(), null, null,
                    name(query.state()), local(query.createdFrom()), local(query.createdTo()), query.page(), query.size()));
            return PageDTO.of(page.records().stream().map(converter::toDTO).toList(), page.total(), page.page(), page.size());
        });
    }

    @Override
    public PageDTO<TradeRefundDTO> refunds(TradeRefundPageQuery query) {
        validate(query);
        var actor = actor(query.payeeMerchantId());
        return transactions.read(actor.tenantId(), () -> {
            var page = queries.refunds(actor.tenantId(), new TradePageFilter(actor.merchantId(), query.orderId(), null, query.refundRequestId(),
                    name(query.state()), local(query.createdFrom()), local(query.createdTo()), query.page(), query.size()));
            return PageDTO.of(page.records().stream().map(converter::toDTO).toList(), page.total(), page.page(), page.size());
        });
    }

    private TradeOrderDTO orderDTO(TradeOrderProjection order, TradeRefundSummary summary) {
        if (summary == null) return converter.toOrderDTO(order, null, null, null, false);
        if (!summary.payeeMerchantId().equals(order.payeeMerchantId()) || !summary.currency().equals(order.amount().getCurrencyValue()))
            throw new CommonException(SNAPSHOT_INVALID);
        return converter.toOrderDTO(order, summary.refundedMinor(), order.paidMinor() - summary.refundedMinor(),
                summary.source().number(), summary.refundedMinor() <= order.paidMinor());
    }

    private RealtimeActor actor(String payee) {
        var actor = actors.current();
        if (actor.merchantId() == null || !actor.merchantId().equals(payee)
                || !actor.permissions().contains("pay:realtime:trade:read")) throw new CommonException(QUERY_FORBIDDEN);
        return actor;
    }

    private void validate(Object query) {
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : BusinessTime.local(value);
    }
}
