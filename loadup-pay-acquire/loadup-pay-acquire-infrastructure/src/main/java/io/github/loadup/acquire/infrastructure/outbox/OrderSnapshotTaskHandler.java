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
package io.github.loadup.acquire.infrastructure.outbox;

import io.github.loadup.acquire.app.service.OrderSnapshotDelivery;
import io.github.loadup.acquire.app.service.OrderSnapshotEvents;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskHandler;
import org.springframework.stereotype.Component;

@Component
public class OrderSnapshotTaskHandler implements TaskHandler {
    private final OrderSnapshotDelivery delivery;

    public OrderSnapshotTaskHandler(OrderSnapshotDelivery delivery) {
        this.delivery = delivery;
    }

    @Override
    public String taskType() {
        return OrderSnapshotEvents.DELIVERY_TYPE;
    }

    @Override
    public void handle(TaskExecution event) {
        delivery.deliver(event);
    }
}
