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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.modules.file.client.dto.FileResourceDTO;
import io.github.loadup.modules.file.client.facade.FileResourceFacade;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.*;

/** FileResource metadata is local; DFS byte transfer is always outside business transactions. */
@Component
public class FileResourceReconciliationSources implements ReconciliationSourceFiles {
    private static final String REFERENCE_TYPE = "reconciliation-import";
    private final FileResourceFacade files;
    private final int maxBytes;
    public FileResourceReconciliationSources(FileResourceFacade files,
            @Value("${loadup.modules.reconciliation.statement-max-bytes:16777216}") int maxBytes) {
        if (maxBytes < 1024 || maxBytes > 67108864) throw new CommonException(INVALID_ARGUMENT);
        this.files = files; this.maxBytes = maxBytes;
    }
    @Override public ReconciliationFileSnapshot upload(String tenant, String owner, String filename, String type, long size, InputStream content) {
        noTransaction();
        if (size <= 0 || size > maxBytes || content == null) throw new CommonException(INPUT_INVALID);
        byte[] bytes = read(content);
        if (bytes.length != size) throw new CommonException(INPUT_INVALID);
        return call(tenant, () -> {
            var stored = files.upload(tenant, owner, filename, type, size, new ByteArrayInputStream(bytes));
            if (stored == null) throw new CommonException(SNAPSHOT_INVALID);
            metadata(stored, tenant, owner, stored.id());
            if (stored.size() != bytes.length) throw new CommonException(SNAPSHOT_INVALID);
            return new ReconciliationFileSnapshot(stored.id(), stored.storageId(), stored.size(), hash(bytes));
        });
    }
    @Override public ReconciliationFileSnapshot inspect(String tenant, String owner, String fileId) {
        noTransaction();
        return call(tenant, () -> {
            var stored = files.get(tenant, fileId, owner, false); metadata(stored, tenant, owner, fileId);
            try (var response = files.download(tenant, fileId, owner, false)) {
                if (response.contentLength() != stored.size() || !stored.storageId().equals(response.metadata().fileId())) throw new CommonException(SNAPSHOT_INVALID);
                byte[] bytes = read(response.content());
                if (bytes.length != stored.size()) throw new CommonException(INPUT_INVALID);
                return new ReconciliationFileSnapshot(fileId, stored.storageId(), stored.size(), hash(bytes));
            } catch (IOException failure) { throw new CommonException(DEPENDENCY_UNAVAILABLE); }
        });
    }
    @Override public void attach(String tenant, String owner, ReconciliationFileSnapshot source, String importId) {
        transaction(tenant, true); current(tenant, owner, source);
        if (!referenced(tenant, owner, source.fileId(), importId)) files.attach(tenant, source.fileId(), owner, false, REFERENCE_TYPE, importId);
    }
    @Override public void verify(String tenant, String owner, ReconciliationFileSnapshot source, String importId) {
        transaction(tenant, false); current(tenant, owner, source);
        if (!referenced(tenant, owner, source.fileId(), importId)) throw new CommonException(SNAPSHOT_INVALID);
    }
    private boolean referenced(String tenant, String owner, String id, String importId) {
        return files.references(tenant, id, owner, false).stream().anyMatch(value -> REFERENCE_TYPE.equals(value.referenceType()) && importId.equals(value.referenceId()));
    }
    private void current(String tenant, String owner, ReconciliationFileSnapshot source) {
        var stored = files.get(tenant, source.fileId(), owner, false); metadata(stored, tenant, owner, source.fileId());
        if (!source.storageId().equals(stored.storageId()) || source.size() != stored.size()) throw new CommonException(SNAPSHOT_INVALID);
    }
    private void metadata(FileResourceDTO value, String tenant, String owner, String id) {
        if (value == null || !Objects.equals(id, value.id()) || !tenant.equals(value.tenantId()) || !owner.equals(value.ownerId())
                || !"ACTIVE".equals(value.state()) || value.size() <= 0 || value.size() > maxBytes || value.storageId() == null)
            throw new CommonException(INPUT_INVALID);
    }
    private byte[] read(InputStream source) {
        try { var bytes = source.readNBytes(maxBytes + 1); if (bytes.length > maxBytes) throw new CommonException(INPUT_INVALID); return bytes; }
        catch (IOException failure) { throw new CommonException(DEPENDENCY_UNAVAILABLE); }
    }
    private String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException failure) { throw new CommonException(DEPENDENCY_UNAVAILABLE, failure); }
    }
    private void noTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE); }
    private void transaction(String tenant, boolean write) {
        if (!Objects.equals(tenant, TenantUtil.getTenantId()) || !TransactionSynchronizationManager.isActualTransactionActive()
                || write && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new CommonException(INVALID_STATE);
    }
    private <T> T call(String tenant, Supplier<T> action) {
        try { return TenantUtil.callWithTenant(tenant, action::get); }
        catch (CommonException failure) {
            if (failure.getResultCode() instanceof io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum) throw failure;
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException failure) { throw new CommonException(DEPENDENCY_UNAVAILABLE); }
    }
}
