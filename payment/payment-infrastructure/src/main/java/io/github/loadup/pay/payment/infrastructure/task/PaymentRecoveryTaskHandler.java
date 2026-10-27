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
package io.github.loadup.pay.payment.infrastructure.task;

import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskHandler;
import io.github.loadup.pay.payment.app.service.PaymentRecoveryService;
import io.github.loadup.pay.payment.app.service.RecoveryExecutionMetrics;
import org.springframework.stereotype.Component;

@Component
public class PaymentRecoveryTaskHandler implements DelayedTaskHandler {
    private final PaymentRecoveryService recovery;
    private final RecoveryExecutionMetrics metrics;

    public PaymentRecoveryTaskHandler(PaymentRecoveryService recovery, RecoveryExecutionMetrics metrics) {
        this.recovery = recovery; this.metrics = metrics;
    }

    @Override
    public String taskType() { return PaymentRecoveryService.TYPE; }

    @Override
    public void handle(DelayedTask task) { metrics.observe(RecoveryExecutionMetrics.Kind.PAYMENT, () -> recovery.handle(task)); }
}
