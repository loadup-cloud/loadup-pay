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

import io.github.loadup.commons.contract.ContractReference;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.CheckoutSession;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionScope;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionState;
import io.github.loadup.pay.acquire.domain.model.OrderAdmissionProof;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentPolicy;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO;
import io.github.loadup.pay.product.client.dto.FeeTermsDTO;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import io.github.loadup.pay.product.client.dto.PaymentTermsDTO;
import io.github.loadup.pay.product.client.dto.RefundTermsDTO;
import io.github.loadup.pay.product.client.enums.AdmissionDecision;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

final class CheckoutFixtures {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0, 0, 123456000);
    static final String OWNER = "a".repeat(64);
    static final String TOKEN = "session." + "A".repeat(43);
    static final String ORIGIN = "https://pay.example";

    static Clock clock(LocalDateTime now) {
        return Clock.fixed(now.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    }

    static StoredAcquireOrder order(AcquireOrderState state, UnpaidOrderAction action, LocalDateTime due) {
        long paid = state == AcquireOrderState.PAID ? 10000 : state == AcquireOrderState.PENDING ? 2000 : 0;
        var order = new AcquireOrder(
                "order",
                "merchant",
                DomainChecks.cnyAmount(10000, "CNY"),
                paid,
                0,
                paid > 0 ? 1 : 0,
                0,
                state,
                due,
                action,
                new OrderPaymentPolicy(1, 100000, null, true),
                0,
                0);
        var reference = new ContractReference("contract", 1, 1, "snapshot", "b".repeat(64));
        return new StoredAcquireOrder(
                order,
                "M001",
                "<script>plain title</script>",
                new OrderAdmissionProof("admission", "order", "merchant", "c".repeat(64), reference),
                NOW.minusMinutes(1),
                OWNER,
                "snapshot-id",
                "admission-json",
                "d".repeat(64));
    }

    static OrderAdmissionDTO admission() {
        return new OrderAdmissionDTO(
                "admission",
                "order",
                "merchant",
                AdmissionDecision.ADMITTED,
                "ALLOW",
                "c".repeat(64),
                "contract",
                1,
                1L,
                "snapshot",
                "b".repeat(64),
                BusinessTime.external(NOW.minusMinutes(1)),
                BusinessTime.external(NOW.plusMinutes(5)),
                terms());
    }

    static StoredCheckoutSession session(CheckoutSessionState state, LocalDateTime expiry) {
        return new StoredCheckoutSession(
                new CheckoutSession("session", "order", state, expiry.truncatedTo(ChronoUnit.SECONDS)),
                "merchant",
                "request",
                "e".repeat(64),
                "f".repeat(64),
                "b".repeat(64),
                "checkout",
                "v1",
                ORIGIN,
                new CheckoutSessionScope(1, OWNER, "return-v1", Set.of("ORDER_SUMMARY", "PAYMENT_START", "PAYMENT_PROGRESS")),
                null, 0);
    }

    static AcquiringTermsDTO terms() {
        return new AcquiringTermsDTO(
                1,
                new OrderTermsDTO(
                        600, 3600, io.github.loadup.pay.product.client.enums.UnpaidOrderAction.CLOSE, false, 0, 300),
                new PaymentTermsDTO(1, 100000, null, true),
                new FeeTermsDTO(
                        new BigDecimal("0.003"),
                        0,
                        RoundingMode.HALF_UP,
                        0,
                        null,
                        io.github.loadup.pay.product.client.enums.FeeRefundPolicy.NO_RETURN),
                new RefundTermsDTO(
                        true, true, 86400, io.github.loadup.pay.product.client.enums.RefundAllocationPolicy.FIFO),
                new NotificationTermsDTO(false, null, null, 1000, 1, BigDecimal.valueOf(2), 60, 5, 3600),
                new LimitPolicyRefDTO(
                        "policy",
                        1,
                        "e".repeat(64),
                        false,
                        null,
                        io.github.loadup.pay.product.client.enums.RefundAmountRestorePolicy.NO_RESTORE,
                        io.github.loadup.pay.product.client.enums.RefundCountRestorePolicy.NO_RESTORE),
                List.of(new ChannelCapabilityDTO(Channel.WECHAT, AccessMode.DIRECT, Set.of(PaymentMethod.H5))),
                "routing-v1",
                "return-v1");
    }

    static final class UnitTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
