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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.port.CashierCapabilities;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class CashierChoices {
    private final CashierCapabilities hosted;
    private final OrderCreationCodec admissions;

    public CashierChoices(CashierCapabilities hosted, OrderCreationCodec admissions) {
        this.hosted = hosted;
        this.admissions = admissions;
    }

    public Set<PaymentChoiceDTO> choices(StoredAcquireOrder order, LocalDateTime at) {
        var policy = order.order().paymentPolicy();
        if (policy.maxSuccessfulPayments() != null && (long) order.order().successfulPaymentCount() + order.order().reservedPaymentCount()
                >= policy.maxSuccessfulPayments()) return Set.of();
        if (!order.order().canAcceptNewPayment(at) || order.order().amount().getCent() - order.order().paidAmountMinor()
                - order.order().paymentReservedMinor() <= 0) return Set.of();
        var original = admissions.decodeAdmission(order.admissionPayload(), order.payloadHash());
        if (original == null || !original.orderId().equals(order.order().id()) || !original.payeeMerchantId().equals(order.order().payeeMerchantId())
                || !original.admissionId().equals(order.admission().admissionId()) || !original.requestDigest().equals(order.admission().requestDigest())
                || original.terms() == null) throw new CommonException(SNAPSHOT_INVALID);
        return hosted.choices().stream().filter(choice -> original.terms().capabilities().stream().anyMatch(capability ->
                capability.channel() == choice.channel() && capability.methods().contains(choice.method()))).collect(Collectors.toUnmodifiableSet());
    }
}
