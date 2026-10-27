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
package io.github.loadup.pay.reconciliation.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.modules.file.client.dto.FileResourceDTO;
import io.github.loadup.modules.file.client.facade.FileResourceFacade;
import io.github.loadup.components.dfs.model.FileDownloadResponse;
import io.github.loadup.components.dfs.model.FileMetadata;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.*;

class FileResourceReconciliationSourcesTest {
    private final FileResourceFacade files = mock(FileResourceFacade.class);
    private final FileResourceReconciliationSources source = new FileResourceReconciliationSources(files, 1024);
    @Test void inspectHashesActualOwnedBytesAndClosesTheDownloadedStream() {
        var closed = new AtomicBoolean();
        when(files.get("tenant", "file", "ops", false)).thenReturn(metadata("ops", 3));
        var object = mock(FileMetadata.class); when(object.fileId()).thenReturn("storage");
        var stream = new ByteArrayInputStream(new byte[]{'a','b','c'}) { @Override public void close() throws IOException { closed.set(true); super.close(); } };
        when(files.download("tenant", "file", "ops", false)).thenReturn(new FileDownloadResponse(object, stream, 3));
        var inspected = source.inspect("tenant", "ops", "file");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", inspected.sha256());
        assertEquals("storage", inspected.storageId()); assertEquals(3L, inspected.size()); assertTrue(closed.get());
    }
    @Test void wrongOwnerOversizedOrTruncatedSourceCannotBeFrozen() {
        when(files.get("tenant", "file", "ops", false)).thenReturn(metadata("other", 3));
        assertEquals(INPUT_INVALID, assertThrows(CommonException.class, () -> source.inspect("tenant", "ops", "file")).getResultCode());
        verify(files, never()).download(anyString(), anyString(), anyString(), anyBoolean());
        when(files.get("tenant", "file", "ops", false)).thenReturn(metadata("ops", 1025));
        assertThrows(CommonException.class, () -> source.inspect("tenant", "ops", "file"));
        when(files.get("tenant", "file", "ops", false)).thenReturn(metadata("ops", 3));
        var object = mock(FileMetadata.class); when(object.fileId()).thenReturn("storage");
        when(files.download("tenant", "file", "ops", false)).thenReturn(new FileDownloadResponse(object, new ByteArrayInputStream(new byte[]{'a'}), 3));
        assertEquals(INPUT_INVALID, assertThrows(CommonException.class, () -> source.inspect("tenant", "ops", "file")).getResultCode());
    }
    @Test void declaredUploadLengthAndByteLimitAreCheckedBeforeCreatingAResource() {
        assertThrows(CommonException.class, () -> source.upload("tenant", "ops", "bill.csv", "text/csv", 3, new ByteArrayInputStream(new byte[]{'a'})));
        assertThrows(CommonException.class, () -> source.upload("tenant", "ops", "bill.csv", "text/csv", 1024, new ByteArrayInputStream(new byte[1025])));
        verifyNoInteractions(files);
    }
    private FileResourceDTO metadata(String owner, long size) {
        var at = LocalDateTime.of(2026, 10, 27, 12, 0);
        return new FileResourceDTO("file", "tenant", owner, "storage", "bill.csv", "text/csv", size, "s3", "ACTIVE", at, at);
    }
}
