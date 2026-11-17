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
package io.github.loadup.reconciliation.infrastructure.task;

import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskHandler;
import io.github.loadup.reconciliation.app.service.ReconciliationStatementExecutionService;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationStatementTaskHandler implements TaskHandler {
    private final ReconciliationStatementExecutionService execution;

    public ReconciliationStatementTaskHandler(ReconciliationStatementExecutionService execution) {
        this.execution = execution;
    }

    @Override
    public String taskType() {
        return ReconciliationStatementExecutionService.TYPE;
    }

    @Override
    public void handle(TaskExecution task) {
        execution.handle(task);
    }
}
