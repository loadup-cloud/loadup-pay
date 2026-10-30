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
package io.github.loadup.pay.reconciliation.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationLedgerConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentCaptures;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActorProvider;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationLedgerImportCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationLedgerPrepareCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationLedgerRetryCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationLedgerSourceDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationLedgerFacade;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_NOT_SEALED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Source RPC and file I/O finish before the short atomic input/batch transaction.
 */
@Service
public class ReconciliationLedgerService implements ReconciliationLedgerFacade {
    public static final String FORMAT = "payment-ledger-capture-v1";
    private final ReconciliationActorProvider actors;
    private final ReconciliationAccountDirectory accounts;
    private final ReconciliationBatchGateway batches;
    private final ReconciliationInputGateway inputs;
    private final ReconciliationPaymentCaptures source;
    private final ReconciliationSourceFiles files;
    private final ReconciliationTransactions transactions;
    private final ReconciliationLedgerConverter ledger;
    private final ReconciliationConverter converter;
    private final IdGenerator ids;
    private final Validator validator;
    private final int maxRecords, maxBytes, fetchSeconds;

    public ReconciliationLedgerService(ReconciliationActorProvider actors, ReconciliationAccountDirectory accounts,
                                       ReconciliationBatchGateway batches, ReconciliationInputGateway inputs, ReconciliationPaymentCaptures source,
                                       ReconciliationSourceFiles files, ReconciliationTransactions transactions, ReconciliationLedgerConverter ledger,
                                       ReconciliationConverter converter, IdGenerator ids, Validator validator,
                                       @Value("${loadup.modules.reconciliation.max-records:10000}") int maxRecords,
                                       @Value("${loadup.modules.reconciliation.statement-max-bytes:16777216}") int maxBytes,
                                       @Value("${loadup.modules.reconciliation.ledger-fetch-seconds:60}") int fetchSeconds) {
        if (maxRecords < 1 || maxRecords > 1000000 || maxBytes < 1 || maxBytes > 67108864 || fetchSeconds < 1 || fetchSeconds > 300) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        this.actors = actors;
        this.accounts = accounts;
        this.batches = batches;
        this.inputs = inputs;
        this.source = source;
        this.files = files;
        this.transactions = transactions;
        this.ledger = ledger;
        this.converter = converter;
        this.ids = ids;
        this.validator = validator;
        this.maxRecords = maxRecords;
        this.maxBytes = maxBytes;
        this.fetchSeconds = fetchSeconds;
    }

    @Override
    public ReconciliationLedgerSourceDTO prepare(ReconciliationLedgerPrepareCommand command) {
        validate(command);
        var actor = actor("prepare");
        var batch = transactions.readSnapshot(actor.tenantId(), () -> owned(actor, command.batchId(), false));
        if (batch.batch().ledger() != null || batch.batch().state() != ReconciliationState.INIT) {
            throw new CommonException(INVALID_STATE);
        }
        // Stable keys are scoped to the original batch and caller, even when remote Create loses its response.
        var requestKey = DigestUtils.sha256(frame(actor.tenantId(), actor.serviceId(), command.batchId(), command.requestKey()));
        var capture = source.create(actor.tenantId(), ledger.toCreate(batch.batch().scope(), requestKey));
        scope(batch.batch().scope(), capture, capture == null ? null : capture.captureId());
        transactions.readSnapshot(actor.tenantId(), () -> owned(actor, command.batchId(), false));
        return ledger.toDTO(capture);
    }

    @Override
    public ReconciliationLedgerSourceDTO retry(ReconciliationLedgerRetryCommand command) {
        validate(command);
        var actor = actor("retry");
        var batch = transactions.readSnapshot(actor.tenantId(), () -> owned(actor, command.batchId(), false));
        var original = source.detail(actor.tenantId(), command.captureId());
        scope(batch.batch().scope(), original, command.captureId());
        if (!command.expectedTaskId().equals(original.nativeTaskId())) {
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        }
        // Recheck the current local grant after source lookup, before the explicit remote retry.
        transactions.readSnapshot(actor.tenantId(), () -> owned(actor, command.batchId(), false));
        var result = source.retry(actor.tenantId(), new io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand(command.captureId(), command.expectedTaskId()));
        scope(batch.batch().scope(), result, command.captureId());
        if (!command.expectedTaskId().equals(result.nativeTaskId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return ledger.toDTO(result);
    }

    @Override
    public ReconciliationBatchDTO importSealed(ReconciliationLedgerImportCommand command) {
        validate(command);
        var actor = actor("import");
        var original = transactions.readSnapshot(actor.tenantId(), () -> owned(actor, command.batchId(), false));
        if (original.batch().ledger() != null) {
            return transactions.readSnapshot(actor.tenantId(), () -> replay(owned(actor, command.batchId(), false), command.captureId()));
        }
        if (original.batch().state() != ReconciliationState.INIT) {
            throw new CommonException(INVALID_STATE);
        }
        long started = System.nanoTime();
        var capture = source.detail(actor.tenantId(), command.captureId());
        scope(original.batch().scope(), capture, command.captureId());
        if (capture.state() != PaymentCaptureState.SEALED) {
            throw new CommonException(INPUT_NOT_SEALED);
        }
        long count = total(capture);
        if (count > maxRecords) {
            throw new CommonException(INPUT_INVALID);
        }
        var rows = new ArrayList<PaymentLedgerLineDTO>((int) count);
        int page = 1;
        while (true) {
            budget(started);
            var result = source.lines(actor.tenantId(), new PaymentCaptureLinesQuery(capture.captureId(), page, 100));
            if (result == null || result.getPageInfo() == null || result.getData() == null || !Objects.equals(result.getPageInfo().totalCount(), count)
                    || !Objects.equals(result.getPageInfo().pageIndex(), (long) page) || !Objects.equals(result.getPageInfo().pageSize(), 100L)) {
                throw new CommonException(INPUT_INVALID);
            }
            long offset = (long) (page - 1) * 100;
            long expected = Math.min(100, Math.max(0L, count - offset));
            if (result.getData().size() != expected) {
                throw new CommonException(INPUT_INVALID);
            }
            for (var row : result.getData()) {
                if (row == null || row.ordinal() != ++offset) {
                    throw new CommonException(INPUT_INVALID);
                }
                date(capture, row);
                rows.add(row);
            }
            if (expected == 0) {
                break; // Explicit empty sentinel even after a short last page.
            }
            page++;
        }
        budget(started);
        if (!matches(capture, rows)) {
            throw new CommonException(INPUT_INVALID);
        }
        var latest = source.detail(actor.tenantId(), command.captureId());
        scope(original.batch().scope(), latest, command.captureId());
        if (latest.state() == PaymentCaptureState.SEALED) {
            total(latest);
        }
        if (latest.state() != PaymentCaptureState.SEALED || !Objects.equals(capture.contentHash(), latest.contentHash())
                || !Objects.equals(capture.startedAt().toInstant(), latest.startedAt().toInstant())
                || !Objects.equals(capture.finishedAt().toInstant(), latest.finishedAt().toInstant())
                || !capture.systemZone().equals(latest.systemZone()) || !matches(latest, rows)) {
            throw new CommonException(INPUT_INVALID);
        }
        budget(started);
        String artifactId = ids.generate();
        var data = dataset(original.batch().scope(), artifactId, capture, rows);
        var manifest = data.manifest();
        final byte[] bytes;
        try {
            bytes = PaymentCaptureContentHash.archive(capture, rows, maxBytes);
        } catch (CommonException invalid) {
            throw new CommonException(INPUT_INVALID);
        }
        var file = files.upload(actor.tenantId(), actor.serviceId(), "payment-ledger-" + capture.captureId() + ".bin", "application/octet-stream", bytes.length, new ByteArrayInputStream(bytes));
        if (file == null || file.size() != bytes.length || !file.sha256().equals(capture.contentHash())) {
            throw new CommonException(INPUT_INVALID);
        }
        return transactions.writeLocal(actor.tenantId(), () -> {
            var current = owned(actor, command.batchId(), true);
            if (!current.batch().scope().equals(original.batch().scope())) {
                throw new CommonException(SCOPE_MISMATCH);
            }
            if (current.batch().ledger() != null) {
                return replay(current, capture.captureId());
            }
            if (current.batch().state() != ReconciliationState.INIT) {
                throw new CommonException(INVALID_STATE);
            }
            files.attach(actor.tenantId(), actor.serviceId(), file, artifactId);
            files.verify(actor.tenantId(), actor.serviceId(), file, artifactId);
            inputs.insertSealed(actor.tenantId(), file.fileId(), data);
            var next = current.batch().attach(manifest);
            batches.update(actor.tenantId(), converter.withBatch(current, next), current.batch().version());
            return converter.toDTO(next);
        });
    }

    private ReconciliationDataset dataset(ReconciliationScope scope, String artifactId, PaymentCaptureDTO capture, List<PaymentLedgerLineDTO> rows) {
        try {
            var lines = rows.stream().map(ledger::toLine).toList();
            var manifest = new ReconciliationManifest(ReconciliationSide.LEDGER, scope, artifactId, capture.contentHash(),
                    ReconciliationDataset.normalizedHash(scope, ReconciliationSide.LEDGER, FORMAT, lines), capture.captureId(), FORMAT, ReconciliationTotals.count(lines));
            return new ReconciliationDataset(manifest, lines);
        } catch (io.github.loadup.commons.error.DomainViolation | ArithmeticException invalidSource) {
            throw new CommonException(INPUT_INVALID);
        }
    }

    private ReconciliationBatchDTO replay(StoredReconciliationBatch batch, String captureId) {
        if (!FORMAT.equals(batch.batch().ledger().parserRevision()) || !captureId.equals(batch.batch().ledger().sourceCaptureId())) {
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        }
        var actual = inputs.sealedManifest(io.github.loadup.commons.util.TenantUtil.getTenantId(), batch.batch().ledger().artifactId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!actual.equals(batch.batch().ledger())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return converter.toDTO(batch.batch());
    }

    private StoredReconciliationBatch owned(ReconciliationActor actor, String id, boolean lock) {
        var stored = (lock ? batches.lock(actor.tenantId(), id) : batches.find(actor.tenantId(), id)).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!actor.serviceId().equals(stored.callerServiceId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        accounts.authorize(actor.tenantId(), actor.serviceId(), stored.channelAccountCode(), stored.batch().scope());
        return stored;
    }

    private void scope(ReconciliationScope scope, PaymentCaptureDTO capture, String id) {
        if (capture == null || id == null || !id.equals(capture.captureId()) || scope.channel() != capture.channel()
                || !scope.accountScopeHash().equals(capture.accountScopeHash()) || !scope.billDate().equals(capture.billDate())
                || !scope.statementProfile().equals(capture.statementProfile()) || capture.state() == null) {
            throw new CommonException(SCOPE_MISMATCH);
        }
    }

    private boolean matches(PaymentCaptureDTO capture, List<PaymentLedgerLineDTO> rows) {
        try {
            return PaymentCaptureContentHash.matches(capture, rows);
        } catch (RuntimeException malformed) {
            throw new CommonException(INPUT_INVALID);
        }
    }

    private long total(PaymentCaptureDTO value) {
        if (value.paymentCount() == null || value.refundCount() == null || value.paymentCount() < 0 || value.refundCount() < 0
                || value.startedAt() == null || value.finishedAt() == null || value.systemZone() == null || value.finishedAt().isBefore(value.startedAt())) {
            throw new CommonException(INPUT_INVALID);
        }
        try {
            return Math.addExact(value.paymentCount(), value.refundCount());
        } catch (ArithmeticException overflow) {
            throw new CommonException(INPUT_INVALID);
        }
    }

    private void date(PaymentCaptureDTO capture, PaymentLedgerLineDTO row) {
        if (row.sourceAt() == null || !row.sourceAt().withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalDate().equals(capture.billDate())) {
            throw new CommonException(INPUT_INVALID);
        }
    }

    private void budget(long started) {
        if (System.nanoTime() - started > (long) fetchSeconds * 1000000000L) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private ReconciliationActor actor(String action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        var actor = actors.current();
        if (actor == null || !actor.permissions().contains("pay:reconciliation:ledger:" + action)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private String frame(String... fields) {
        var text = new StringBuilder("reconciliation-ledger-request-v1");
        for (var field : fields) {
            text.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
        return text.toString();
    }
}
