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
package io.github.loadup.pay.payment.infrastructure.converter;

import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RefundResultStorageConverterTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundResultStorageConverter.class.getPackageName());
    final RefundResultStorageConverter converter = context.getBean(RefundResultStorageConverter.class);
    final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
    final ChannelPaymentIdentity original = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "revision", "a".repeat(64), "payment-request");
    @AfterEach void close() { context.close(); }
    @Test void permanentClaimRoundTripsExactOriginalAccountAndBothTransactionNumbers() {
        var claim = new RefundTransactionClaim("claim", "tenant", "refund", original, "refund-request", "original-transaction",
                DomainChecks.amount(300, "CNY"), "refund-transaction", now, "receipt");
        var row = converter.toClaimDO(claim);
        assertEquals(claim, converter.toClaim(row, converter.toIdentity(row)));
        assertNull(row.getCreatedAt()); assertNull(row.getUpdatedAt()); assertEquals("tenant", row.getTenantId());
    }
    @Test void aStartPatchOnlyWritesProgressAndTheOneWaySubmissionMarker() {
        var child = RefundOrder.init("refund", "request", "payment", DomainChecks.amount(300, "CNY"), "refund-request");
        var request = RefundRequest.init("request", "order", "merchant", "R001", child.amount(), List.of(child));
        var capacity = new PaymentRefundCapacity("payment", "order", "merchant", now.minusHours(1), DomainChecks.amount(1000, "CNY"), 0, 300, 1);
        var stored = new StoredRefundChild(child, 1, original, "original-transaction", now.minusHours(1));
        var started = child.beginSubmission();
        var change = new RefundProgressChange(new RefundExecutionFacts(stored, null, null, null), started, request, request.apply(started),
                capacity, capacity, now, null, null);
        var row = converter.toChildPatch(change);
        assertEquals("PROCESSING", row.getState()); assertEquals(1, row.getRowVersion().longValue()); assertEquals(now, row.getSubmissionStartedAt());
        assertNull(row.getAmountMinor()); assertNull(row.getPaymentId()); assertNull(row.getOriginalChannelTransactionNo());
        assertNull(row.getChannelRefundTransactionNo()); assertNull(row.getSucceededAt());
        var header = converter.toRequestPatch(change.request());
        assertEquals(0, header.getSucceededMinor().longValue()); assertEquals(0, header.getFailedMinor().longValue());
        assertEquals(1, header.getRowVersion().longValue()); assertNull(header.getAmountMinor()); assertNull(header.getRefundTerms());
    }
}
