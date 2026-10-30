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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.channel.api.model.ChannelNotificationAcknowledgement;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.converter.RefundCallbackObservationConverter;
import io.github.loadup.pay.payment.app.port.ChannelCallbackEndpointResolver;
import io.github.loadup.pay.payment.app.port.RefundResultCodec;
import io.github.loadup.pay.payment.client.facade.RefundChannelCallbackFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelNotificationGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundCallbackTargetGateway;
import io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.OUTCOME_UNKNOWN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Refund notifications trigger original-request queries; only committed refund facts permit ACK.
 */
@Service
public class RefundChannelCallbackService implements RefundChannelCallbackFacade {
    private final ChannelCallbackEndpointResolver endpoints;
    private final RefundCallbackTargetGateway targets;
    private final PaymentChannelNotificationGateway notifications;
    private final PaymentChannelGateway channels;
    private final RefundResultService results;
    private final RefundResultCodec facts;
    private final RefundCallbackObservationConverter converter;
    private final PaymentAcceptanceTransactions transactions;

    public RefundChannelCallbackService(ChannelCallbackEndpointResolver endpoints, RefundCallbackTargetGateway targets,
                                        PaymentChannelNotificationGateway notifications, PaymentChannelGateway channels, RefundResultService results,
                                        RefundResultCodec facts, RefundCallbackObservationConverter converter, PaymentAcceptanceTransactions transactions) {
        this.endpoints = endpoints;
        this.targets = targets;
        this.notifications = notifications;
        this.channels = channels;
        this.results = results;
        this.facts = facts;
        this.converter = converter;
        this.transactions = transactions;
    }

    @Override
    public ChannelNotificationAcknowledgement receive(String endpointRef, ChannelNotificationInput input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        var endpoint = endpoints.resolve(endpointRef).orElseThrow(() -> new CommonException(NOT_FOUND));
        boolean accepted;
        try {
            accepted = transactions.withTenantContext(endpoint.tenantId(), () -> {
                var hint = notifications.identifyRefund(endpoint.channel(), endpoint.bindingRevisionId(), input);
                var target = targets.find(endpoint.tenantId(), endpoint.bindingRevisionId(), hint.channelRequestNo(), hint.channelRefundNo())
                        .orElseThrow(() -> new CommonException(NOT_FOUND));
                if (target.original().channel() != endpoint.channel() || !target.original().bindingRevisionId().equals(endpoint.bindingRevisionId())
                        || !target.original().channelRequestNo().equals(hint.channelRequestNo()) || !target.channelRefundNo().equals(hint.channelRefundNo())) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                var verified = notifications.verifyRefund(target, input);
                if (!target.equals(verified.refund())) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                var query = channels.queryRefund(target);
                if (query == null || query.source() != RefundObservation.Source.QUERY_RESPONSE || !target.refundId().equals(query.refundId())
                        || !target.original().equals(query.original()) || !target.channelRefundNo().equals(query.channelRefundNo())
                        || !target.originalChannelTransactionNo().equals(query.originalChannelTransactionNo()) || !target.amount().equals(query.amount())
                        || query.channelRefundTransactionNo() != null && !verified.channelRefundTransactionNo().equals(query.channelRefundTransactionNo())) {
                    throw new CommonException(OUTCOME_UNKNOWN);
                }
                String notificationId = verified.notificationId();
                String key = "refund-notification:" + DigestUtils.sha256(notificationId.getBytes(StandardCharsets.UTF_8).length
                        + ":" + notificationId + facts.factsDigest(query));
                String source = DigestUtils.sha256("refund-notification-source:" + verified.sourceDigest() + ":" + query.sourceDigest());
                return results.record(endpoint.tenantId(), converter.toObservation(query, key, source)).disposition() != RefundReceiptDisposition.CONFLICT;
            });
        } catch (RuntimeException rejected) {
            accepted = false;
        }
        return notifications.acknowledgement(endpoint.channel(), accepted);
    }
}
