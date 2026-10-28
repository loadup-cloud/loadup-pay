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

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.payment.app.model.RecoveryInventoryCell;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.github.loadup.pay.payment.app.model.RecoveryInventoryCell.Kind.PAYMENT;
import static io.github.loadup.pay.payment.app.model.RecoveryInventoryCell.Kind.REFUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoveryInventoryCellTest {
    @Test
    void mergeKeepsTheOldestBusinessTimeAndRejectsOverflowOrDifferentKinds() {
        var at = LocalDateTime.of(2026, 10, 27, 12, 0);
        var first = new RecoveryInventoryCell(PAYMENT, 2L, 1000L, at);
        var older = new RecoveryInventoryCell(PAYMENT, 1L, 500L, at.minusHours(1));
        assertEquals(new RecoveryInventoryCell(PAYMENT, 3L, 1500L, at.minusHours(1)), first.plus(older));
        assertEquals(first, first.plus(RecoveryInventoryCell.empty(PAYMENT)));
        assertThrows(DomainViolation.class, () -> first.plus(RecoveryInventoryCell.empty(REFUND)));
        assertThrows(ArithmeticException.class, () -> first.plus(new RecoveryInventoryCell(PAYMENT, Long.MAX_VALUE, 1L, at)));
        assertThrows(DomainViolation.class, () -> new RecoveryInventoryCell(PAYMENT, 0L, 100L, null));
        assertThrows(DomainViolation.class, () -> new RecoveryInventoryCell(PAYMENT, 1L, 100L, null));
    }
}
