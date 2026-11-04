/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.payment.infrastructure.repository;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.payment.channel.api.model.ChannelBindingRevision;
import io.github.loadup.components.payment.channel.api.model.WechatAccountIdentity;
import io.github.loadup.payment.app.port.ChannelBindingCodec;
import io.github.loadup.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.payment.domain.model.ChannelBindingState;
import io.github.loadup.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.payment.domain.model.PaymentAttempt;
import io.github.loadup.payment.domain.model.PaymentOrder;
import io.github.loadup.payment.domain.model.StoredChannelBindingRevision;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.productcenter.client.dto.AcquiringTermsDTO;
import io.github.loadup.productcenter.client.dto.AdmittedOrderTermsDTO;
import io.github.loadup.productcenter.client.dto.ChannelCapabilityDTO;
import io.github.loadup.productcenter.client.dto.FeeTermsDTO;
import io.github.loadup.productcenter.client.dto.LimitPolicyRefDTO;
import io.github.loadup.productcenter.client.dto.NotificationTermsDTO;
import io.github.loadup.productcenter.client.dto.OrderTermsDTO;
import io.github.loadup.productcenter.client.dto.PaymentTermsDTO;
import io.github.loadup.productcenter.client.dto.RefundTermsDTO;
import io.github.loadup.productcenter.client.enums.FeeRefundPolicy;
import io.github.loadup.productcenter.client.enums.RefundAllocationPolicy;
import io.github.loadup.productcenter.client.enums.RefundAmountRestorePolicy;
import io.github.loadup.productcenter.client.enums.RefundCountRestorePolicy;
import io.github.loadup.productcenter.client.enums.UnpaidOrderAction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Synthetic inputs and legitimate domain transitions; persistence is always handled by real generated Mappers/Gateways.
 */
final class FinancialPersistenceFixtures {
    static final LocalDateTime AT = LocalDateTime.of(2026, 10, 26, 12, 0);

    static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    static Fixture prepared(PaymentAcceptanceCodec codec, ChannelBindingCodec bindings) {
        String tenant = id("tenant"), payment = id("payment"), order = id("order"), merchant = id("merchant");
        var terms = new AdmittedOrderTermsDTO(
                id("admission"),
                order,
                merchant,
                10000L,
                "CNY",
                "contract",
                1,
                1L,
                "snapshot",
                "b".repeat(64),
                terms());
        var command = new PaymentExecuteCommand(
                payment,
                order,
                id("allocation"),
                terms.admissionId(),
                merchant,
                10000L,
                "CNY",
                "a".repeat(64),
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5),
                null,
                terms,
                "Synthetic acquiring order",
                BusinessTime.external(AT.plusMinutes(10)));
        var identity = new WechatAccountIdentity("synthetic-app", "synthetic-mchid", null, null);
        var binding = new ChannelBindingRevision(
                id("revision"),
                merchant,
                Channel.WECHAT,
                AccessMode.DIRECT,
                bindings.accountScopeHash(Channel.WECHAT, identity),
                "synthetic-credential",
                "v1",
                identity,
                Set.of(PaymentMethod.H5));
        String headId = id("binding");
        var head = new ChannelMerchantBinding(
                        headId,
                        merchant,
                        "primary",
                        Channel.WECHAT,
                        AccessMode.DIRECT,
                        ChannelBindingState.INIT,
                        null,
                        0,
                        0,
                        0L)
                .publish(binding, 1, 0);
        var revision = new StoredChannelBindingRevision(
                binding,
                headId,
                1,
                bindings.checksum(bindings.encodeIdentity(identity)),
                "pay-ops",
                AT.minusMinutes(1));
        var attempt = PaymentAttempt.init(
                id("attempt"),
                payment,
                1,
                Money.ofMinor(10000L, "CNY"),
                binding,
                PaymentMethod.H5,
                UUID.randomUUID().toString().replace("-", ""));
        String operation = id("operation");
        var storedAttempt = new StoredPaymentAttempt(
                attempt,
                operation,
                "acquire",
                "first-attempt",
                "c".repeat(64),
                command.requestDigest(),
                null,
                null,
                null,
                AT,
                null,
                0L);
        var aggregate = PaymentOrder.init(
                        payment, order, command.allocationId(), merchant, attempt.amount(), operation, AT)
                .prepareAttempt(attempt, null);
        String payload = codec.encode(command);
        var stored = new StoredPaymentAcceptance(
                aggregate,
                command.admissionId(),
                command.requestDigest(),
                "acquire",
                payload,
                codec.checksum(payload),
                null);
        return new Fixture(tenant, command, stored, storedAttempt, head, revision);
    }

    static AcquiringTermsDTO terms() {
        return new AcquiringTermsDTO(
                1,
                new OrderTermsDTO(600L, 3600L, UnpaidOrderAction.CLOSE, false, 0L, 300L),
                new PaymentTermsDTO(1, 100000, null, true),
                new FeeTermsDTO(new BigDecimal("0.003"), 0, RoundingMode.HALF_UP, 0, null, FeeRefundPolicy.NO_RETURN),
                new RefundTermsDTO(true, true, 86400L, RefundAllocationPolicy.FIFO),
                new NotificationTermsDTO(false, null, null, 1000, 1, BigDecimal.valueOf(2), 60, 5, 3600),
                new LimitPolicyRefDTO(
                        "policy",
                        1,
                        "e".repeat(64),
                        false,
                        null,
                        RefundAmountRestorePolicy.NO_RESTORE,
                        RefundCountRestorePolicy.NO_RESTORE),
                List.of(new ChannelCapabilityDTO(Channel.WECHAT, AccessMode.DIRECT, Set.of(PaymentMethod.H5))),
                "routing-v1",
                "return-v1");
    }

    record Fixture(
            String tenant,
            PaymentExecuteCommand command,
            StoredPaymentAcceptance payment,
            StoredPaymentAttempt attempt,
            ChannelMerchantBinding head,
            StoredChannelBindingRevision revision) {
        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }
}
