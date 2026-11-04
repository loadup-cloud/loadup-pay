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
package io.github.loadup.payment.app.service;

import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.payment.client.dto.PaymentChoiceDTO;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

final class PaymentFixtures {
    static final String SUBJECT = "Test payment subject";
    static final OffsetDateTime PAYABLE_UNTIL = OffsetDateTime.parse("2030-01-01T00:00:00+08:00");

    static PaymentExecuteCommand command() {
        return new PaymentExecuteCommand(
                "payment",
                "order",
                "allocation",
                "admission",
                "merchant",
                4000,
                "CNY",
                "a".repeat(64),
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5),
                null,
                new AdmittedOrderTermsDTO(
                        "admission",
                        "order",
                        "merchant",
                        10000,
                        "CNY",
                        "contract",
                        1,
                        1,
                        "snapshot",
                        "b".repeat(64),
                        terms()),
                SUBJECT,
                PAYABLE_UNTIL);
    }

    static AcquiringTermsDTO terms() {
        return new AcquiringTermsDTO(
                1,
                new OrderTermsDTO(600, 3600, UnpaidOrderAction.CLOSE, false, 0, 300),
                new PaymentTermsDTO(1, 100000, null, true),
                new FeeTermsDTO(new BigDecimal("0.003"), 0, RoundingMode.HALF_UP, 0, null, FeeRefundPolicy.NO_RETURN),
                new RefundTermsDTO(true, true, 86400, RefundAllocationPolicy.FIFO),
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
}
