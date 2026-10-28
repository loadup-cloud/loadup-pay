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
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptBinding;
import io.github.loadup.pay.payment.domain.model.PaymentExecutionContext;
import io.github.loadup.pay.payment.domain.model.PaymentGateBinding;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;

import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Payment-local error mapping for the pure binding. No persistence, codecs, actor lookup or execution side effects.
 */
public final class PaymentExecutionBindings {
    private PaymentExecutionBindings() {
    }

    public static LockedPayment locked(StoredPaymentAcceptance accepted, StoredPaymentGate parent, StoredPaymentGate gate) {
        return checked(() -> new LockedPayment(new PaymentGateBinding(accepted, parent, gate)), SNAPSHOT_INVALID);
    }

    public static PaymentGateBinding openGates(LockedPayment locked) {
        return checked(() -> locked.binding().requireOpen(), EXECUTION_FENCED);
    }

    public static PaymentExecutionContext context(LockedPayment locked, StoredPaymentAttempt stored) {
        return checked(() -> new PaymentExecutionContext(locked.binding(), new PaymentAttemptBinding(locked.payment(), stored)), SNAPSHOT_INVALID);
    }

    public static PaymentExecutionContext activeContext(LockedPayment locked, StoredPaymentAttempt stored) {
        var context = context(locked, stored);
        return checked(context::requireActive, SNAPSHOT_INVALID);
    }

    public static PaymentExecutionContext submittableContext(LockedPayment locked, StoredPaymentAttempt stored) {
        var context = context(locked, stored);
        return checked(context::requireSubmittable, EXECUTION_FENCED);
    }

    public static PaymentExecutionContext observableContext(LockedPayment locked, StoredPaymentAttempt stored) {
        var context = context(locked, stored);
        return checked(context::requireObservable, SNAPSHOT_INVALID);
    }

    public static PaymentExecutionContext closableContext(LockedPayment locked, StoredPaymentAttempt stored) {
        var context = context(locked, stored);
        return checked(context::requireClosable, SNAPSHOT_INVALID);
    }

    public static PaymentAttemptBinding bound(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        return checked(() -> new PaymentAttemptBinding(accepted, stored), SNAPSHOT_INVALID);
    }

    public static PaymentAttemptBinding active(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        return checked(() -> new PaymentAttemptBinding(accepted, stored).requireActive(), SNAPSHOT_INVALID);
    }

    public static PaymentAttemptBinding submittable(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        return checked(() -> new PaymentAttemptBinding(accepted, stored).requireSubmittable(), SNAPSHOT_INVALID);
    }

    public static PaymentAttemptBinding observable(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        return checked(() -> new PaymentAttemptBinding(accepted, stored).requireObservable(), SNAPSHOT_INVALID);
    }

    public static PaymentAttemptBinding closable(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        return checked(() -> new PaymentAttemptBinding(accepted, stored).requireClosable(), SNAPSHOT_INVALID);
    }

    public static PaymentAttemptBinding submissionRequest(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        return checked(() -> new PaymentAttemptBinding(accepted, stored).requireSubmissionRequest(), INVALID_STATE);
    }

    public static PaymentAttemptBinding route(PaymentAttemptBinding binding, Channel channel, PaymentMethod method, String preparedInputRef) {
        return checked(() -> binding.requireRoute(channel, method, preparedInputRef), SNAPSHOT_INVALID);
    }

    public static PaymentAttemptBinding.ObservationRejection observationRejection(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored,
                                                                                  ChannelPaymentIdentity identity, Money amount) {
        return checked(() -> PaymentAttemptBinding.observationRejection(accepted, stored, identity, amount), SNAPSHOT_INVALID);
    }

    private static <T> T checked(Supplier<T> action, PaymentResultCodeEnum failureCode) {
        try {
            return action.get();
        } catch (DomainViolation | NullPointerException invalid) {
            throw new CommonException(failureCode);
        }
    }
}
