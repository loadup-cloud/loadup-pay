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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.codec.ChannelActionCodec;
import io.github.loadup.pay.channel.api.model.AlipayH5ChannelAction;
import io.github.loadup.pay.channel.api.model.ChannelPaymentExecution;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentActionGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentActionSource;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAction;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.OUTCOME_UNKNOWN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Persist original actions under execution locks; local preparations never fabricate channel receipts.
 */
@Service
public class PaymentActionService {
    private final PaymentActionGateway actions;
    private final PaymentAttemptGateway attempts;
    private final PaymentAcceptanceCodec codec;
    private final PaymentAcceptanceConverter views;
    private final PaymentChannelInputConverter channelViews;
    private final Clock clock;
    private final ChannelActionCodec actionCodec;

    public PaymentActionService(PaymentActionGateway actions, PaymentAttemptGateway attempts, PaymentAcceptanceCodec codec,
                                PaymentAcceptanceConverter views, PaymentChannelInputConverter channelViews, @Qualifier("paymentClock") Clock clock, ChannelActionCodec actionCodec) {
        this.actions = actions;
        this.attempts = attempts;
        this.codec = codec;
        this.views = views;
        this.channelViews = channelViews;
        this.clock = clock;
        this.actionCodec = actionCodec;
    }

    public void record(String tenant, LockedPayment locked, ChannelPaymentExecution original,
                       ChannelPaymentSubmission result, String receiptId) {
        writable(tenant);
        if (original == null || result == null || !original.identity().equals(result.identity())
                || !original.amount().equals(result.amount())) throw new CommonException(INVALID_ARGUMENT);
        if (result.action() == null) return;
        boolean local = result.observation() == null;
        if (local ? receiptId != null || !(result.action() instanceof AlipayH5ChannelAction)
                || !(original instanceof PreparedChannelPayment)
                : receiptId == null || result.observation().kind() != ChannelPaymentOutcome.Kind.ACCEPTED)
            throw new CommonException(INVALID_ARGUMENT);
        if (!active(locked) || !original.identity().attemptId().equals(locked.payment().payment().activeAttemptId()))
            return;
        var attempt = attempts.find(tenant, original.identity().attemptId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        validate(locked, attempt);
        if (!attempt.attempt().identity().equals(original.identity())
                || attempt.attempt().executionEpoch() != original.executionEpoch()
                || attempt.attempt().method() != original.method() || !attempt.attempt().amount().equals(original.amount()))
            throw new CommonException(OUTCOME_UNKNOWN);
        if (original instanceof PreparedChannelPayment prepared
                && !java.util.Objects.equals(prepared.preparedInputRef(), attempt.preparedInputRef()))
            throw new CommonException(OUTCOME_UNKNOWN);
        var command = codec.decode(locked.payment());
        var action = result.action();
        if (action.method() != original.method() || action.expiresAt().isAfter(command.payableUntil()))
            throw new CommonException(INVALID_ARGUMENT);
        var issued = clock.instant();
        if (!issued.isBefore(action.expiresAt().toInstant())) return;
        var proposed = new StoredPaymentAction(original.identity(), original.amount(), original.executionEpoch(), action,
                local ? PaymentActionSource.LOCAL_SIGNED : PaymentActionSource.CHANNEL_RECEIPT, receiptId,
                local ? actionCodec.checksum(actionCodec.encode(action)) : result.observation().sourceDigest(),
                LocalDateTime.ofInstant(issued, clock.getZone()));
        var existing = actions.find(tenant, original.identity().attemptId());
        if (existing.isPresent()) {
            var prior = existing.get();
            if (!prior.identity().equals(proposed.identity()) || !prior.amount().equals(proposed.amount())
                    || prior.executionEpoch() != proposed.executionEpoch() || prior.source() != proposed.source()
                    || !prior.action().equals(proposed.action()))
                throw new CommonException(OUTCOME_UNKNOWN);
            return;
        }
        actions.insert(tenant, proposed);
    }

    public PaymentExecutionDTO present(String tenant, LockedPayment locked) {
        readable(tenant);
        var base = views.toDTO(locked.payment());
        if (!active(locked)) return base;
        var attemptId = locked.payment().payment().activeAttemptId();
        if (attemptId == null) return base;
        var stored = actions.find(tenant, attemptId);
        if (stored.isEmpty()) return base;
        var current = attempts.find(tenant, attemptId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        validate(locked, current);
        var action = stored.get();
        if (!action.identity().equals(current.attempt().identity()) || !action.amount().equals(current.attempt().amount())
                || action.executionEpoch() != current.attempt().executionEpoch() || action.action().method() != current.attempt().method()
                || action.action().expiresAt().isAfter(codec.decode(locked.payment()).payableUntil()))
            throw new CommonException(SNAPSHOT_INVALID);
        if (!clock.instant().isBefore(action.action().expiresAt().toInstant())) return base;
        return views.withAction(base, channelViews.toAction(action.action()));
    }

    private boolean active(LockedPayment locked) {
        return (locked.payment().payment().state() == PaymentState.PROCESSING
                || locked.payment().payment().state() == PaymentState.UNKNOWN)
                && locked.parent().gate().state() == GateState.OPEN && locked.gate().gate().state() == GateState.OPEN;
    }

    private void validate(LockedPayment locked, StoredPaymentAttempt stored) {
        var accepted = locked.payment();
        PaymentExecutionBindings.activeContext(locked, stored);
        var attempt = stored.attempt();
        if (attempt.executionEpoch() <= 0
                || (attempt.state() != PaymentAttemptState.SUBMITTING && attempt.state() != PaymentAttemptState.PENDING
                && attempt.state() != PaymentAttemptState.UNKNOWN))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private void readable(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }

    private void writable(String tenant) {
        readable(tenant);
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new CommonException(INVALID_STATE);
    }
}

