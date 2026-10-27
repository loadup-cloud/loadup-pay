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
package io.github.loadup.pay.reconciliation.infrastructure.task;

import io.github.loadup.modules.transfer.client.enums.TransferKind;
import io.github.loadup.modules.transfer.client.spi.TransferContext;
import io.github.loadup.modules.transfer.client.spi.TransferHandler;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationImportExecutionService;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.io.OutputStream;

/** Native Transfer owns source streams, task claims, acknowledgements and temporary-file cleanup. */
@Component
public class ReconciliationImportTaskHandler implements TransferHandler {
    private final ReconciliationImportExecutionService service;
    public ReconciliationImportTaskHandler(ReconciliationImportExecutionService service) { this.service = service; }
    @Override public String key() { return ReconciliationImportExecutionService.HANDLER_KEY; }
    @Override public TransferKind kind() { return TransferKind.IMPORT; }
    @Override public String outputFilename() { return "reconciliation-import.txt"; }
    @Override public String outputContentType() { return "text/plain"; }
    @Override public void process(TransferContext context, InputStream input, OutputStream output) { service.handle(context, input); }
}
