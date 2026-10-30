package io.github.loadup.pay.reconciliation.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.ResultCode;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchTaskDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationInputDTO;
import io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.rpc.ReconciliationRpcFields;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchQuery;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchRetryCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchTaskDTO;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationInputDTO;
import java.util.Set;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(config=LoadUpMapStructConfig.class, componentModel="default", uses={ReconciliationRpcFields.class}, nullValueCheckStrategy=NullValueCheckStrategy.ALWAYS)
public interface ReconciliationRpcMapper {
    ReconciliationRpcMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(ReconciliationRpcMapper.class);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="requestKey", source="requestKey"), @Mapping(target="channelAccountCode", source="channelAccountCode"), @Mapping(target="billDate", source="billDate")})
    public WireReconciliationBatchCreateCommand toWire(ReconciliationBatchCreateCommand value);

    public ReconciliationBatchCreateCommand fromWire(WireReconciliationBatchCreateCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="batchId", source="batchId"), @Mapping(target="side", source="side"), @Mapping(target="artifactId", source="artifactId")})
    public WireReconciliationInputAttachCommand toWire(ReconciliationInputAttachCommand value);

    public ReconciliationInputAttachCommand fromWire(WireReconciliationInputAttachCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mapping(target="batchId", source="batchId")
    public WireReconciliationBatchStartCommand toWire(ReconciliationBatchStartCommand value);

    public ReconciliationBatchStartCommand fromWire(WireReconciliationBatchStartCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="batchId", source="batchId"), @Mapping(target="runId", source="runId"), @Mapping(target="expectedTaskId", source="expectedTaskId")})
    public WireReconciliationBatchRetryCommand toWire(ReconciliationBatchRetryCommand value);

    public ReconciliationBatchRetryCommand fromWire(WireReconciliationBatchRetryCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="runId", source="runId"), @Mapping(target="taskId", source="taskId"), @Mapping(target="nativeStatus", source="nativeStatus")})
    public WireReconciliationBatchTaskDTO toWire(ReconciliationBatchTaskDTO value);

    public ReconciliationBatchTaskDTO fromWire(WireReconciliationBatchTaskDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="batchId", source="batchId"), @Mapping(target="channel", source="channel"), @Mapping(target="accountScopeHash", source="accountScopeHash"), @Mapping(target="billDate", source="billDate"), @Mapping(target="statementProfile", source="statementProfile"), @Mapping(target="state", source="state"), @Mapping(target="channelInput", source="channelInput"), @Mapping(target="ledgerInput", source="ledgerInput"), @Mapping(target="runId", source="runId"), @Mapping(target="matchedCount", source="matchedCount"), @Mapping(target="differenceCount", source="differenceCount"), @Mapping(target="resultHash", source="resultHash"), @Mapping(target="version", source="version"), @Mapping(target="taskId", source="taskId"), @Mapping(target="nativeStatus", source="nativeStatus")})
    public WireReconciliationBatchDTO toWire(ReconciliationBatchDTO value);

    public ReconciliationBatchDTO fromWire(WireReconciliationBatchDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="side", source="side"), @Mapping(target="artifactId", source="artifactId"), @Mapping(target="artifactSha256", source="artifactSha256"), @Mapping(target="normalizedSha256", source="normalizedSha256"), @Mapping(target="sourceCaptureId", source="sourceCaptureId"), @Mapping(target="parserRevision", source="parserRevision"), @Mapping(target="paymentCount", source="paymentCount"), @Mapping(target="paymentAmountMinor", source="paymentAmountMinor"), @Mapping(target="refundCount", source="refundCount"), @Mapping(target="refundAmountMinor", source="refundAmountMinor")})
    public WireReconciliationInputDTO toWire(ReconciliationInputDTO value);

    public ReconciliationInputDTO fromWire(WireReconciliationInputDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mapping(target="id", source="id")
    public WireReconciliationBatchQuery toWire(IdQuery value);

    public IdQuery fromWire(WireReconciliationBatchQuery value);

    @BeforeMapping
    default public void required(WireReconciliationBatchCreateCommand value) {
        if (!(value == null || value.hasRequestKey() && value.hasChannelAccountCode() && value.hasBillDate())) {
            this.invalid();
        }
    }

    @BeforeMapping
    default public void required(WireReconciliationInputAttachCommand value) {
        if (!(value == null || value.hasBatchId() && value.hasSide() && value.hasArtifactId())) {
            this.invalid();
        }
    }

    @BeforeMapping
    default public void required(WireReconciliationBatchStartCommand value) {
        if (value != null && !value.hasBatchId()) {
            this.invalid();
        }
    }

    @BeforeMapping
    default public void required(WireReconciliationBatchRetryCommand value) {
        if (!(value == null || value.hasBatchId() && value.hasRunId() && value.hasExpectedTaskId())) {
            this.invalid();
        }
    }

    @BeforeMapping
    default public void required(WireReconciliationBatchTaskDTO value) {
        if (!(value == null || value.hasRunId() && value.hasTaskId() && value.hasNativeStatus())) {
            this.invalid();
        }
    }

    @BeforeMapping
    default public void required(WireReconciliationBatchQuery value) {
        if (value != null && !value.hasId()) {
            this.invalid();
        }
    }

    @BeforeMapping
    default public void required(WireReconciliationInputDTO value) {
        if (value == null) {
            return;
        }
        if (!(value.hasSide() && value.hasArtifactId() && value.hasArtifactSha256() && value.hasNormalizedSha256() && value.hasSourceCaptureId() && value.hasParserRevision() && value.hasPaymentCount() && value.hasPaymentAmountMinor() && value.hasRefundCount() && value.hasRefundAmountMinor() && value.getPaymentCount() >= 0L && value.getRefundCount() >= 0L && value.getPaymentAmountMinor() >= 0L && value.getRefundAmountMinor() >= 0L)) {
            this.invalid();
        }
        this.text(value.getArtifactId(), 64);
        this.text(value.getSourceCaptureId(), 64);
        this.text(value.getParserRevision(), 64);
        this.hash(value.getArtifactSha256());
        this.hash(value.getNormalizedSha256());
        if (value.getPaymentCount() == 0L != (value.getPaymentAmountMinor() == 0L) || value.getRefundCount() == 0L != (value.getRefundAmountMinor() == 0L) || value.getPaymentAmountMinor() < value.getPaymentCount() || value.getRefundAmountMinor() < value.getRefundCount()) {
            this.invalid();
        }
        try {
            Math.addExact(value.getPaymentCount(), value.getRefundCount());
        }
        catch (ArithmeticException overflow) {
            this.invalid();
        }
    }

    @BeforeMapping
    default public void required(WireReconciliationBatchDTO value) {
        boolean started;
        ReconciliationState state;
        if (value == null) {
            return;
        }
        if (!(value.hasBatchId() && value.hasChannel() && value.hasAccountScopeHash() && value.hasBillDate() && value.hasStatementProfile() && value.hasState() && value.hasVersion() && value.getVersion() >= 0L)) {
            this.invalid();
        }
        if (value.hasTaskId() != value.hasNativeStatus() || value.hasTaskId() && !value.hasRunId()) {
            this.invalid();
        }
        if (value.hasTaskId()) {
            this.text(value.getTaskId(), 64);
            if (!Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED").contains(value.getNativeStatus())) {
                this.invalid();
            }
        }
        boolean complete = (state = ReconciliationRpcFields.state(value.getState())) == ReconciliationState.MATCHED || state == ReconciliationState.DIFFERENCES;
        boolean bl = started = complete || state == ReconciliationState.RUNNING;
        if (started != value.hasRunId() || complete != value.hasMatchedCount() || complete != value.hasDifferenceCount() || complete != value.hasResultHash() || state == ReconciliationState.INIT == (value.hasChannelInput() && value.hasLedgerInput())) {
            this.invalid();
        }
        this.text(value.getBatchId(), 64);
        this.text(value.getStatementProfile(), 64);
        this.hash(value.getAccountScopeHash());
        if (started) {
            this.text(value.getRunId(), 64);
        }
        if (complete) {
            this.hash(value.getResultHash());
        }
        if (value.hasChannelInput() && value.hasLedgerInput() && (value.getChannelInput().getArtifactId().equals(value.getLedgerInput().getArtifactId()) || value.getChannelInput().getSourceCaptureId().equals(value.getLedgerInput().getSourceCaptureId()))) {
            this.invalid();
        }
        if (complete && (value.getMatchedCount() < 0L || value.getDifferenceCount() < 0L || state == ReconciliationState.MATCHED != (value.getDifferenceCount() == 0L))) {
            this.invalid();
        }
        if (value.hasChannelInput() && !"CHANNEL".equals(value.getChannelInput().getSide()) || value.hasLedgerInput() && !"LEDGER".equals(value.getLedgerInput().getSide())) {
            this.invalid();
        }
    }

    private void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            this.invalid();
        }
    }

    private void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            this.invalid();
        }
    }

    private void invalid() {
        throw new CommonException((ResultCode)ReconciliationResultCodeEnum.INVALID_ARGUMENT);
    }
}
