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
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.payment.app.converter.PaymentCallbackObservationConverter;
import io.github.loadup.pay.payment.app.port.ChannelCallbackEndpointResolver;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentResultCodec;
import io.github.loadup.pay.payment.client.facade.PaymentChannelCallbackFacade;
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** A signed notification triggers an original-account query; only committed Y3 facts permit the provider ACK. */
@Service
public class PaymentChannelCallbackService implements PaymentChannelCallbackFacade {
    private static final System.Logger LOG = System.getLogger(PaymentChannelCallbackService.class.getName());
    private final ChannelCallbackEndpointResolver endpoints;
    private final PaymentAttemptGateway attempts;
    private final PaymentAcceptanceGateway payments;
    private final PaymentChannelNotificationGateway notifications;
    private final PaymentChannelGateway channels;
    private final PaymentResultService results;
    private final PaymentAcceptanceCodec commands;
    private final PaymentResultCodec observations;
    private final PaymentCallbackObservationConverter converter;
    private final PaymentAcceptanceTransactions transactions;

    public PaymentChannelCallbackService(ChannelCallbackEndpointResolver endpoints, PaymentAttemptGateway attempts,
            PaymentAcceptanceGateway payments, PaymentChannelNotificationGateway notifications, PaymentChannelGateway channels,
            PaymentResultService results, PaymentAcceptanceCodec commands, PaymentResultCodec observations,
            PaymentCallbackObservationConverter converter, PaymentAcceptanceTransactions transactions) {
        this.endpoints = endpoints; this.attempts = attempts; this.payments = payments; this.notifications = notifications;
        this.channels = channels; this.results = results; this.commands = commands; this.observations = observations;
        this.converter = converter; this.transactions = transactions;
    }

    @Override public ChannelNotificationAcknowledgement receive(String endpointRef, ChannelNotificationInput input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var endpoint = endpoints.resolve(endpointRef).orElseThrow(() -> new CommonException(NOT_FOUND));
        boolean accepted;
        try {
            accepted = transactions.read(endpoint.tenantId(), () -> {
                var hint = notifications.identify(endpoint.channel(), input);
                var stored = attempts.findChannelRequest(endpoint.tenantId(), endpoint.bindingRevisionId(), hint.channelRequestNo())
                        .orElseThrow(() -> new CommonException(NOT_FOUND));
                var attempt = stored.attempt();
                if (attempt.binding().channel() != endpoint.channel() || !attempt.binding().id().equals(endpoint.bindingRevisionId())
                        || !attempt.channelRequestNo().equals(hint.channelRequestNo()) || attempt.executionEpoch() <= 0
                        || stored.submissionStartedAt() == null) throw new CommonException(SNAPSHOT_INVALID);
                var payment = payments.find(endpoint.tenantId(), attempt.paymentId()).orElseThrow(() -> new CommonException(NOT_FOUND));
                commands.decode(payment);
                if (!payment.payment().amount().equals(attempt.amount())
                        || !payment.payment().payeeMerchantId().equals(attempt.binding().payeeMerchantId())
                        || !payment.payment().limitOperationId().equals(stored.limitOperationId())
                        || !payment.callerServiceId().equals(stored.callerServiceId())
                        || !payment.requestDigest().equals(stored.paymentRequestDigest())) throw new CommonException(SNAPSHOT_INVALID);
                var target = new ChannelPaymentTarget(attempt.identity(), attempt.amount());
                var verified = notifications.verify(target, input);
                // Nothing from the unverified hint reaches a channel query or a financial mutation.
                if (!verified.identity().equals(target.identity()) || !verified.amount().equals(target.amount()))
                    throw new CommonException(SNAPSHOT_INVALID);
                var query = channels.query(target);
                if (query.source() != PaymentObservation.Source.QUERY_RESPONSE || !target.identity().equals(query.identity())
                        || !target.amount().equals(query.amount()) || query.channelTransactionNo() != null
                        && !verified.channelTransactionNo().equals(query.channelTransactionNo())) throw new CommonException(OUTCOME_UNKNOWN);
                String notificationId = verified.notificationId();
                String facts = observations.factsDigest(query);
                String key = "notification:" + DigestUtils.sha256(notificationId.getBytes(StandardCharsets.UTF_8).length
                        + ":" + notificationId + facts);
                String source = DigestUtils.sha256("notification-source:" + verified.sourceDigest() + ":" + query.sourceDigest());
                var result = results.record(endpoint.tenantId(), converter.toObservation(query, key, source));
                return result.disposition() != PaymentReceiptDisposition.CONFLICT;
            });
        } catch (RuntimeException rejected) {
            String code = rejected instanceof CommonException failure ? failure.getResultCode().getCode() : rejected.getClass().getSimpleName();
            LOG.log(System.Logger.Level.WARNING, "Channel callback rejected: {0}", code);
            accepted = false;
        }
        return notifications.acknowledgement(endpoint.channel(), accepted);
    }
}
