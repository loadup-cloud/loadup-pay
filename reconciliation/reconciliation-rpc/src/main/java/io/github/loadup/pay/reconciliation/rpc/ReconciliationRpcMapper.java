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
package io.github.loadup.pay.reconciliation.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationInputDTO;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchQuery;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationInputDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;

@Mapper(config = LoadUpMapStructConfig.class, uses = ReconciliationRpcFields.class,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface ReconciliationRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "requestKey", source = "requestKey")
    @Mapping(target = "channelAccountCode", source = "channelAccountCode")
    @Mapping(target = "billDate", source = "billDate")
    WireReconciliationBatchCreateCommand toWire(ReconciliationBatchCreateCommand value);

    ReconciliationBatchCreateCommand fromWire(WireReconciliationBatchCreateCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "batchId", source = "batchId")
    @Mapping(target = "side", source = "side")
    @Mapping(target = "artifactId", source = "artifactId")
    WireReconciliationInputAttachCommand toWire(ReconciliationInputAttachCommand value);

    ReconciliationInputAttachCommand fromWire(WireReconciliationInputAttachCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "batchId", source = "batchId")
    WireReconciliationBatchStartCommand toWire(ReconciliationBatchStartCommand value);

    ReconciliationBatchStartCommand fromWire(WireReconciliationBatchStartCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "batchId", source = "batchId")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "accountScopeHash", source = "accountScopeHash")
    @Mapping(target = "billDate", source = "billDate")
    @Mapping(target = "statementProfile", source = "statementProfile")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "channelInput", source = "channelInput")
    @Mapping(target = "ledgerInput", source = "ledgerInput")
    @Mapping(target = "runId", source = "runId")
    @Mapping(target = "matchedCount", source = "matchedCount")
    @Mapping(target = "differenceCount", source = "differenceCount")
    @Mapping(target = "resultHash", source = "resultHash")
    @Mapping(target = "version", source = "version")
    WireReconciliationBatchDTO toWire(ReconciliationBatchDTO value);

    ReconciliationBatchDTO fromWire(WireReconciliationBatchDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "side", source = "side")
    @Mapping(target = "artifactId", source = "artifactId")
    @Mapping(target = "artifactSha256", source = "artifactSha256")
    @Mapping(target = "normalizedSha256", source = "normalizedSha256")
    @Mapping(target = "sourceCaptureId", source = "sourceCaptureId")
    @Mapping(target = "parserRevision", source = "parserRevision")
    @Mapping(target = "paymentCount", source = "paymentCount")
    @Mapping(target = "paymentAmountMinor", source = "paymentAmountMinor")
    @Mapping(target = "refundCount", source = "refundCount")
    @Mapping(target = "refundAmountMinor", source = "refundAmountMinor")
    WireReconciliationInputDTO toWire(ReconciliationInputDTO value);

    ReconciliationInputDTO fromWire(WireReconciliationInputDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    WireReconciliationBatchQuery toWire(IdQuery value);

    IdQuery fromWire(WireReconciliationBatchQuery value);

    @BeforeMapping
    default void required(WireReconciliationBatchCreateCommand value) {
        if (value != null && (!value.hasRequestKey() || !value.hasChannelAccountCode() || !value.hasBillDate()))
            invalid();
    }

    @BeforeMapping
    default void required(WireReconciliationInputAttachCommand value) {
        if (value != null && (!value.hasBatchId() || !value.hasSide() || !value.hasArtifactId())) invalid();
    }

    @BeforeMapping
    default void required(WireReconciliationBatchStartCommand value) {
        if (value != null && !value.hasBatchId()) invalid();
    }

    @BeforeMapping
    default void required(WireReconciliationBatchQuery value) {
        if (value != null && !value.hasId()) invalid();
    }

    @BeforeMapping
    default void required(WireReconciliationInputDTO value) {
        if (value == null) return;
        if (!value.hasSide() || !value.hasArtifactId() || !value.hasArtifactSha256() || !value.hasNormalizedSha256()
                || !value.hasSourceCaptureId() || !value.hasParserRevision() || !value.hasPaymentCount()
                || !value.hasPaymentAmountMinor() || !value.hasRefundCount() || !value.hasRefundAmountMinor()
                || value.getPaymentCount() < 0 || value.getRefundCount() < 0 || value.getPaymentAmountMinor() < 0 || value.getRefundAmountMinor() < 0)
            invalid();
        text(value.getArtifactId(), 64);
        text(value.getSourceCaptureId(), 64);
        text(value.getParserRevision(), 64);
        hash(value.getArtifactSha256());
        hash(value.getNormalizedSha256());
        if ((value.getPaymentCount() == 0) != (value.getPaymentAmountMinor() == 0)
                || (value.getRefundCount() == 0) != (value.getRefundAmountMinor() == 0)
                || value.getPaymentAmountMinor() < value.getPaymentCount() || value.getRefundAmountMinor() < value.getRefundCount())
            invalid();
        try {
            Math.addExact(value.getPaymentCount(), value.getRefundCount());
        } catch (ArithmeticException overflow) {
            invalid();
        }
    }

    @BeforeMapping
    default void required(WireReconciliationBatchDTO value) {
        if (value == null) return;
        if (!value.hasBatchId() || !value.hasChannel() || !value.hasAccountScopeHash() || !value.hasBillDate()
                || !value.hasStatementProfile() || !value.hasState() || !value.hasVersion() || value.getVersion() < 0)
            invalid();
        var state = ReconciliationRpcFields.state(value.getState());
        boolean complete = state == io.github.loadup.pay.reconciliation.client.enums.ReconciliationBatchState.MATCHED
                || state == io.github.loadup.pay.reconciliation.client.enums.ReconciliationBatchState.DIFFERENCES;
        boolean started = complete || state == io.github.loadup.pay.reconciliation.client.enums.ReconciliationBatchState.RUNNING;
        if (started != value.hasRunId() || complete != value.hasMatchedCount() || complete != value.hasDifferenceCount()
                || complete != value.hasResultHash()
                || (state == io.github.loadup.pay.reconciliation.client.enums.ReconciliationBatchState.INIT)
                == (value.hasChannelInput() && value.hasLedgerInput())) invalid();
        text(value.getBatchId(), 64);
        text(value.getStatementProfile(), 64);
        hash(value.getAccountScopeHash());
        if (started) text(value.getRunId(), 64);
        if (complete) hash(value.getResultHash());
        if (value.hasChannelInput() && value.hasLedgerInput()
                && (value.getChannelInput().getArtifactId().equals(value.getLedgerInput().getArtifactId())
                || value.getChannelInput().getSourceCaptureId().equals(value.getLedgerInput().getSourceCaptureId())))
            invalid();
        if (complete && (value.getMatchedCount() < 0 || value.getDifferenceCount() < 0
                || (state == io.github.loadup.pay.reconciliation.client.enums.ReconciliationBatchState.MATCHED) != (value.getDifferenceCount() == 0)))
            invalid();
        if (value.hasChannelInput() && !"CHANNEL".equals(value.getChannelInput().getSide())
                || value.hasLedgerInput() && !"LEDGER".equals(value.getLedgerInput().getSide())) invalid();
    }

    private void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            invalid();
    }

    private void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) invalid();
    }

    private void invalid() {
        throw new CommonException(INVALID_ARGUMENT);
    }
}
