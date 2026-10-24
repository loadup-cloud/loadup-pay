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
package io.github.loadup.pay.acquire.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.dto.CashierOrderDTO;
import io.github.loadup.pay.acquire.client.dto.CheckoutDTO;
import io.github.loadup.pay.acquire.domain.model.CheckoutSession;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionScope;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.commons.time.BusinessTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface CheckoutConverter {
    @Mapping(target = "revokedAt", ignore = true)
    StoredCheckoutSession toStored(
            CheckoutSession session,
            String payeeMerchantId,
            String requestKey,
            String requestDigest,
            String tokenHash,
            String tokenNonce,
            String tokenKeyRef,
            String tokenKeyVersion,
            String allowedOrigin,
            CheckoutSessionScope scope,
            long rowVersion);

    CheckoutDTO toCheckout(AcquireOrderDTO order, String sessionId, String checkoutUrl, LocalDateTime sessionExpiresAt);

    @Mapping(target = "merchantDisplayName", ignore = true)
    @Mapping(target = "choices", source = "choices")
    @Mapping(target = "minimumPaymentMinor", source = "policy.minAmountMinor")
    @Mapping(target = "maximumPaymentMinor", source = "policy.maxAmountMinor")
    @Mapping(target = "allowExactRemainderBelowMin", source = "policy.allowExactRemainderBelowMin")
    @Mapping(target = "remainingPaymentCount", expression = "java(policy.maxSuccessfulPayments() == null ? null : Integer.valueOf((int) Math.max(0L, policy.maxSuccessfulPayments().longValue() - occupiedCount)))")
    CashierOrderDTO toSummary(AcquireOrderDTO order, String subject, LocalDateTime sessionExpiresAt,
            java.util.Set<io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO> choices,
            io.github.loadup.pay.acquire.domain.model.OrderPaymentPolicy policy, long occupiedCount);

    default OffsetDateTime external(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
