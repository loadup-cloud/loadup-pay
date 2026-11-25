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
package io.github.loadup.components.payment.channel.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.money.Money;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ChannelObservationContractTest {
    @Test
    void paymentObservationRequiresConsistentSuccessAndFailureFacts() {
        var success = ApiFixtures.succeededObservation();
        assertEquals(LocalDateTime.parse("2026-10-23T10:11:12.123456"), success.succeededAt());
        assertThrows(
                RuntimeException.class,
                () -> observation(
                        ChannelPaymentOutcome.Kind.SUCCEEDED,
                        null,
                        ApiFixtures.SUCCEEDED_AT,
                        PaymentObservation.Source.QUERY_RESPONSE));
        assertThrows(
                RuntimeException.class,
                () -> observation(
                        ChannelPaymentOutcome.Kind.SUCCEEDED,
                        "trade-1",
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE));
        assertThrows(
                RuntimeException.class,
                () -> observation(
                        ChannelPaymentOutcome.Kind.ACCEPTED,
                        null,
                        ApiFixtures.SUCCEEDED_AT,
                        PaymentObservation.Source.QUERY_RESPONSE));
        assertThrows(
                RuntimeException.class,
                () -> observation(
                        ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED,
                        null,
                        ApiFixtures.SUCCEEDED_AT,
                        PaymentObservation.Source.CLOSE_RESPONSE));
        assertEquals(
                ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED,
                observation(
                                ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED,
                                "trade-1",
                                ApiFixtures.SUCCEEDED_AT,
                                PaymentObservation.Source.CLOSE_RESPONSE)
                        .kind());
        assertThrows(
                RuntimeException.class,
                () -> new PaymentObservation(
                        null,
                        ChannelPaymentOutcome.Kind.ACCEPTED,
                        ApiFixtures.AMOUNT,
                        null,
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new PaymentObservation(
                        ApiFixtures.IDENTITY,
                        null,
                        ApiFixtures.AMOUNT,
                        null,
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new PaymentObservation(
                        ApiFixtures.IDENTITY,
                        ChannelPaymentOutcome.Kind.ACCEPTED,
                        ApiFixtures.AMOUNT,
                        null,
                        null,
                        null,
                        "receipt-1",
                        ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new PaymentObservation(
                        ApiFixtures.IDENTITY,
                        ChannelPaymentOutcome.Kind.ACCEPTED,
                        Money.ofMinor(0, "CNY"),
                        null,
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new PaymentObservation(
                        ApiFixtures.IDENTITY,
                        ChannelPaymentOutcome.Kind.ACCEPTED,
                        Money.ofMinor(1250, "USD"),
                        null,
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new PaymentObservation(
                        ApiFixtures.IDENTITY,
                        ChannelPaymentOutcome.Kind.ACCEPTED,
                        ApiFixtures.AMOUNT,
                        " ",
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new PaymentObservation(
                        ApiFixtures.IDENTITY,
                        ChannelPaymentOutcome.Kind.ACCEPTED,
                        ApiFixtures.AMOUNT,
                        null,
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        " ",
                        ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new PaymentObservation(
                        ApiFixtures.IDENTITY,
                        ChannelPaymentOutcome.Kind.ACCEPTED,
                        ApiFixtures.AMOUNT,
                        null,
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        "bad"));
    }

    @Test
    void refundObservationRequiresTerminalReferenceAndMonotonicAcceptance() {
        assertEquals(
                ChannelRefundOutcome.Kind.ACCEPTED,
                ApiFixtures.acceptedRefundObservation().kind());
        var succeeded = new RefundObservation(
                "refund-1",
                ApiFixtures.IDENTITY,
                "refund-no",
                "trade-1",
                ChannelRefundOutcome.Kind.SUCCEEDED,
                ApiFixtures.REFUND_AMOUNT,
                "refund-tx",
                ApiFixtures.SUCCEEDED_AT,
                RefundObservation.Source.QUERY_RESPONSE,
                "receipt-1",
                ApiFixtures.HASH,
                null);
        assertEquals("refund-tx", succeeded.channelRefundTransactionNo());
        assertEquals(LocalDateTime.parse("2026-10-23T10:11:12.123456"), succeeded.succeededAt());
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        " ",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.ACCEPTED,
                        ApiFixtures.REFUND_AMOUNT,
                        null,
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        null,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.ACCEPTED,
                        ApiFixtures.REFUND_AMOUNT,
                        null,
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        " ",
                        "trade-1",
                        ChannelRefundOutcome.Kind.ACCEPTED,
                        ApiFixtures.REFUND_AMOUNT,
                        null,
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        " ",
                        ChannelRefundOutcome.Kind.ACCEPTED,
                        ApiFixtures.REFUND_AMOUNT,
                        null,
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        null,
                        ApiFixtures.REFUND_AMOUNT,
                        null,
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.ACCEPTED,
                        Money.ofMinor(0, "CNY"),
                        null,
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.ACCEPTED,
                        ApiFixtures.REFUND_AMOUNT,
                        null,
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        " ",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.ACCEPTED,
                        ApiFixtures.REFUND_AMOUNT,
                        null,
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        "bad",
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.SUCCEEDED,
                        ApiFixtures.REFUND_AMOUNT,
                        null,
                        ApiFixtures.SUCCEEDED_AT,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.ACCEPTED,
                        ApiFixtures.REFUND_AMOUNT,
                        "refund-tx",
                        null,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.SUCCEEDED,
                        ApiFixtures.REFUND_AMOUNT,
                        "refund-tx",
                        ApiFixtures.SUCCEEDED_AT,
                        RefundObservation.Source.QUERY_RESPONSE,
                        "receipt-1",
                        ApiFixtures.HASH,
                        ApiFixtures.SUCCEEDED_AT.plusSeconds(1)));
        assertThrows(
                RuntimeException.class,
                () -> new RefundObservation(
                        "refund-1",
                        ApiFixtures.IDENTITY,
                        "refund-no",
                        "trade-1",
                        ChannelRefundOutcome.Kind.SUCCEEDED,
                        ApiFixtures.REFUND_AMOUNT,
                        "refund-tx",
                        ApiFixtures.SUCCEEDED_AT,
                        null,
                        "receipt-1",
                        ApiFixtures.HASH,
                        null));
    }

    @Test
    void paymentAndRefundOutcomesRequireEvidenceForTerminalKinds() {
        assertEquals(
                ChannelPaymentOutcome.Kind.ACCEPTED,
                new ChannelPaymentOutcome(
                                ChannelPaymentOutcome.Kind.ACCEPTED, ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, null)
                        .kind());
        assertEquals(
                ChannelPaymentOutcome.Kind.SUCCEEDED,
                new ChannelPaymentOutcome(
                                ChannelPaymentOutcome.Kind.SUCCEEDED,
                                ApiFixtures.IDENTITY,
                                ApiFixtures.AMOUNT,
                                "evidence-1")
                        .kind());
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentOutcome(
                        ChannelPaymentOutcome.Kind.SUCCEEDED, ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentOutcome(null, ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, "evidence-1"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentOutcome(ChannelPaymentOutcome.Kind.ACCEPTED, null, ApiFixtures.AMOUNT, null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentOutcome(
                        ChannelPaymentOutcome.Kind.ACCEPTED, ApiFixtures.IDENTITY, Money.ofMinor(1250, "USD"), null));
        assertEquals(
                ChannelRefundOutcome.Kind.SUCCEEDED,
                new ChannelRefundOutcome(
                                "refund-1",
                                ChannelRefundOutcome.Kind.SUCCEEDED,
                                ApiFixtures.REFUND_AMOUNT,
                                "evidence-1")
                        .kind());
        assertThrows(
                RuntimeException.class,
                () -> new ChannelRefundOutcome(
                        "refund-1", ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND, ApiFixtures.REFUND_AMOUNT, null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelRefundOutcome(
                        " ", ChannelRefundOutcome.Kind.ACCEPTED, ApiFixtures.REFUND_AMOUNT, null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelRefundOutcome("refund-1", null, ApiFixtures.REFUND_AMOUNT, null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelRefundOutcome(
                        "refund-1", ChannelRefundOutcome.Kind.ACCEPTED, Money.ofMinor(500, "USD"), null));
    }

    @Test
    void verifiedNotificationsCarryOnlyAuthenticatedTriggerMetadata() {
        var notification = new VerifiedChannelNotification(
                ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, "notification-1", "trade-1", "SUCCESS", ApiFixtures.HASH);
        assertEquals("notification-1", notification.notificationId());
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelNotification(
                        null, ApiFixtures.AMOUNT, "notification-1", "trade-1", "SUCCESS", ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelNotification(
                        ApiFixtures.IDENTITY,
                        Money.ofMinor(0, "CNY"),
                        "notification-1",
                        "trade-1",
                        "SUCCESS",
                        ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelNotification(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, " ", "trade-1", "SUCCESS", ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelNotification(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, "notification-1", " ", "SUCCESS", ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelNotification(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, "notification-1", "trade-1", " ", ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelNotification(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, "notification-1", "trade-1", "SUCCESS", "bad"));
        var refund = new VerifiedChannelRefundNotification(
                ApiFixtures.refund(), "notification-1", "refund-tx", "SUCCESS", ApiFixtures.HASH);
        assertEquals("refund-tx", refund.channelRefundTransactionNo());
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelRefundNotification(
                        null, "notification-1", "refund-tx", "SUCCESS", ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelRefundNotification(
                        ApiFixtures.refund(), " ", "refund-tx", "SUCCESS", ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelRefundNotification(
                        ApiFixtures.refund(), "notification-1", " ", "SUCCESS", ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelRefundNotification(
                        ApiFixtures.refund(), "notification-1", "refund-tx", " ", ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelRefundNotification(
                        ApiFixtures.refund(), "notification-1", "refund-tx", "SUCCESS", "bad"));
    }

    private static PaymentObservation observation(
            ChannelPaymentOutcome.Kind kind,
            String transactionNo,
            LocalDateTime succeededAt,
            PaymentObservation.Source source) {
        return new PaymentObservation(
                ApiFixtures.IDENTITY,
                kind,
                ApiFixtures.AMOUNT,
                transactionNo,
                succeededAt,
                source,
                "receipt-1",
                ApiFixtures.HASH);
    }
}
