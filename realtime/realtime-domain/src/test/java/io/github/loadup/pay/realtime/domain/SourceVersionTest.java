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
package io.github.loadup.pay.realtime.domain;

import io.github.loadup.pay.common.*;
import io.github.loadup.pay.realtime.domain.model.SourceVersion;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceVersionTest {
    @Test
    void versionsOfDifferentProducersOrAggregatesAreNotComparable() {
        var original = new SourceVersion("acquire", "AcquireOrder", "order", 5);
        assertTrue(new SourceVersion("acquire", "AcquireOrder", "order", 6).newerThan(original));
        assertFalse(new SourceVersion("acquire", "AcquireOrder", "order", 4).newerThan(original));
        assertThrows(
                DomainViolation.class,
                () -> new SourceVersion("payment", "OrderRefundSummary", "order", 100).newerThan(original));
    }
}
