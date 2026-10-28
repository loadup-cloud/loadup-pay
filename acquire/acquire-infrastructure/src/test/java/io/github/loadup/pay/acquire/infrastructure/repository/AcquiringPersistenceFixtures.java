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
package io.github.loadup.pay.acquire.infrastructure.repository;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand;
import io.github.loadup.pay.acquire.domain.model.PaymentInteractionKind;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderFee;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO;
import io.github.loadup.pay.product.client.dto.FeeTermsDTO;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import io.github.loadup.pay.product.client.dto.PaymentTermsDTO;
import io.github.loadup.pay.product.client.dto.RefundTermsDTO;
import io.github.loadup.pay.product.domain.model.AdmissionDecision;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Complete synthetic A1 evidence; creation intents, snapshots, headers and fee rows use actual gateways.
 */
final class AcquiringPersistenceFixtures {
    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 26, 12, 0);
    static final String OWNER = "a".repeat(64);

    static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    static Fixture order(OrderCreationCodec codec, AcquireOrderConverter converter, String mode) {
        String tenant = id("tenant"), orderId = id("order"), merchant = id("merchant"), merchantNo = id("merchant-no");
        var command = new AcquireOrderCreateCommand(merchant, merchantNo, "scope", 10000L, "CNY", "Synthetic acquiring order", null);
        String payload;
        String digest;
        if ("DIRECT_PAY".equals(mode)) {
            var direct = new DirectPayRecoverCommand(merchant, merchantNo, "scope", 10000L, "CNY", command.subject(), null,
                    "original-first", 10000L, new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), PaymentInteractionKind.NONE);
            payload = codec.encodeDirectPay(direct);
            digest = codec.directPayDigest(tenant, OWNER, direct);
        } else {
            payload = codec.encodeIntent(command);
            digest = codec.requestDigest(tenant, OWNER, command);
        }
        var intent = new OrderCreateIntent(id("intent"), orderId, merchant, merchantNo, digest, OWNER, mode, null, payload,
                "INIT", null, null, null, 0L);
        var admittedAt = AT.minusMinutes(1);
        var admission = new OrderAdmissionDTO(id("admission"), orderId, merchant, AdmissionDecision.ADMITTED, "ALLOW", digest,
                "contract", 1, 1L, "snapshot", "b".repeat(64), BusinessTime.external(admittedAt), BusinessTime.external(AT.plusMinutes(5)), terms());
        String admittedPayload = codec.encodeAdmission(admission);
        var aggregate = AcquireOrder.init(orderId, merchant, Money.ofMinor(10000L, "CNY"), admittedAt.plusSeconds(600),
                UnpaidOrderAction.CLOSE, converter.toPaymentPolicy(admission.terms().payment()));
        var stored = converter.toStored(aggregate, command, converter.toProof(admission), admittedAt, OWNER, id("snapshot"),
                admittedPayload, codec.checksum(admittedPayload));
        var fee = OrderFee.init(id("fee"), orderId, aggregate.amount(), converter.toFeePolicy(admission.terms().fee()));
        return new Fixture(tenant, intent, stored, fee, codec.encodeFee(fee.policy()), admission);
    }

    static AcquiringTermsDTO terms() {
        return new AcquiringTermsDTO(1,
                new OrderTermsDTO(600L, 3600L, io.github.loadup.pay.product.domain.model.UnpaidOrderAction.CLOSE, false, 0L, 300L),
                new PaymentTermsDTO(1, 100000, null, true),
                new FeeTermsDTO(new BigDecimal("0.003"), 0, RoundingMode.HALF_UP, 0, null,
                        io.github.loadup.pay.product.domain.model.FeeRefundPolicy.NO_RETURN),
                new RefundTermsDTO(true, true, 86400L, io.github.loadup.pay.product.domain.model.RefundAllocationPolicy.FIFO),
                new NotificationTermsDTO(false, null, null, 1000, 1, BigDecimal.valueOf(2), 60, 5, 3600),
                new LimitPolicyRefDTO("policy", 1, "e".repeat(64), false, null,
                        io.github.loadup.pay.product.domain.model.RefundAmountRestorePolicy.NO_RESTORE,
                        io.github.loadup.pay.product.domain.model.RefundCountRestorePolicy.NO_RESTORE),
                List.of(new ChannelCapabilityDTO(Channel.WECHAT, AccessMode.DIRECT, Set.of(PaymentMethod.H5))), "routing-v1", "return-v1");
    }

    record Fixture(String tenant, OrderCreateIntent intent, StoredAcquireOrder stored, OrderFee fee, String feePayload,
                   OrderAdmissionDTO admission) {
        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }
}
