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
package io.github.loadup.pay.limitcenter.domain.model;

import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** A trusted final payment outcome, not a timeout or a caller's claim. */
public record LimitReleaseEvidence(
        String paymentId, Money amount, long paymentVersion, String evidenceId, boolean executionFenced) {
    public LimitReleaseEvidence {
        DomainChecks.id(paymentId);
        DomainChecks.id(evidenceId);
        Objects.requireNonNull(amount);
        require(paymentVersion > 0 && executionFenced, INVALID_ARGUMENT);
    }
}
