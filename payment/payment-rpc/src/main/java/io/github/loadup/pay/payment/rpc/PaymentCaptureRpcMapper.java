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
package io.github.loadup.pay.payment.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.*;
import io.github.loadup.components.rpc.grpc.GrpcScalarMapper;
import io.github.loadup.pay.payment.client.command.*;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.proto.*;
import org.mapstruct.*;
import java.util.*;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

@Mapper(config = LoadUpMapStructConfig.class, uses = {PaymentCaptureRpcFields.class, GrpcScalarMapper.class},
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface PaymentCaptureRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "captureId", source = "captureId")
    @Mapping(target = "expectedTaskId", source = "expectedTaskId")
    WirePaymentCaptureRetryCommand toWire(PaymentCaptureRetryCommand value);
    PaymentCaptureRetryCommand fromWire(WirePaymentCaptureRetryCommand value);
    @BeforeMapping default void required(WirePaymentCaptureRetryCommand value) {
        if (value == null) return;
        if (!value.hasCaptureId() || !value.hasExpectedTaskId()) invalid(); text(value.getCaptureId(), 64); text(value.getExpectedTaskId(), 64);
    }
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "requestKey", source = "requestKey")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "accountScopeHash", source = "accountScopeHash")
    @Mapping(target = "billDate", source = "billDate")
    @Mapping(target = "statementProfile", source = "statementProfile")
    WirePaymentCaptureCreateCommand toWire(PaymentCaptureCreateCommand value);
    PaymentCaptureCreateCommand fromWire(WirePaymentCaptureCreateCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    WirePaymentCaptureQuery toWire(IdQuery value);
    IdQuery fromWire(WirePaymentCaptureQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "captureId", source = "captureId")
    @Mapping(target = "page", source = "page")
    @Mapping(target = "size", source = "size")
    WirePaymentCaptureLinesQuery toWire(PaymentCaptureLinesQuery value);
    PaymentCaptureLinesQuery fromWire(WirePaymentCaptureLinesQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "captureId", source = "captureId")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "accountScopeHash", source = "accountScopeHash")
    @Mapping(target = "billDate", source = "billDate")
    @Mapping(target = "statementProfile", source = "statementProfile")
    @Mapping(target = "systemZone", source = "systemZone")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "startedAt", source = "startedAt")
    @Mapping(target = "finishedAt", source = "finishedAt")
    @Mapping(target = "paymentCount", source = "paymentCount")
    @Mapping(target = "paymentAmountMinor", source = "paymentAmountMinor")
    @Mapping(target = "refundCount", source = "refundCount")
    @Mapping(target = "refundAmountMinor", source = "refundAmountMinor")
    @Mapping(target = "contentHash", source = "contentHash")
    @Mapping(target = "blocker", source = "blocker")
    @Mapping(target = "blockerSubjectId", source = "blockerSubjectId")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "nativeTaskId", source = "nativeTaskId")
    @Mapping(target = "nativeStatus", source = "nativeStatus")
    @Mapping(target = "attempts", source = "attempts")
    @Mapping(target = "maxAttempts", source = "maxAttempts")
    WirePaymentCaptureDTO toWire(PaymentCaptureDTO value);
    PaymentCaptureDTO fromWire(WirePaymentCaptureDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "ordinal", source = "ordinal")
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "channelTransactionNo", source = "channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "channelRefundNo")
    @Mapping(target = "merchantPaymentNo", source = "merchantPaymentNo")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "sourceAt", source = "sourceAt")
    @Mapping(target = "sourceId", source = "sourceId")
    @Mapping(target = "evidenceId", source = "evidenceId")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    WirePaymentLedgerLineDTO toWire(PaymentLedgerLineDTO value);
    PaymentLedgerLineDTO fromWire(WirePaymentLedgerLineDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "totalCount", source = "pageInfo.totalCount")
    @Mapping(target = "page", expression = "java(Math.toIntExact(value.getPageInfo().pageIndex()))")
    @Mapping(target = "size", expression = "java(Math.toIntExact(value.getPageInfo().pageSize()))")
    WirePaymentCapturePage toWire(PageDTO<PaymentLedgerLineDTO> value);

    @Mapping(target = "data", source = "dataList")
    @Mapping(target = "pageInfo", source = "value")
    PageDTO<PaymentLedgerLineDTO> fromWire(WirePaymentCapturePage value);

    @Mapping(target = "totalCount", source = "totalCount")
    @Mapping(target = "pageIndex", source = "page")
    @Mapping(target = "pageSize", source = "size")
    PageInfo metadata(WirePaymentCapturePage value);
    List<WirePaymentLedgerLineDTO> toWireLines(Collection<PaymentLedgerLineDTO> value);

    @AfterMapping default void entries(PageDTO<PaymentLedgerLineDTO> value, @MappingTarget WirePaymentCapturePage.Builder target) {
        if (value != null && value.getData() != null) target.addAllData(toWireLines(value.getData()));
    }
    @BeforeMapping default void required(WirePaymentCaptureCreateCommand value) {
        if (value == null) return;
        if (!value.hasRequestKey() || !value.hasChannel() || !value.hasAccountScopeHash() || !value.hasBillDate() || !value.hasStatementProfile()) invalid();
        text(value.getRequestKey(), 128); scope(value.getChannel(), value.getAccountScopeHash(), value.getBillDate(), value.getStatementProfile());
    }
    @BeforeMapping default void required(WirePaymentCaptureQuery value) {
        if (value != null) { if (!value.hasId()) invalid(); text(value.getId(), 64); }
    }
    @BeforeMapping default void required(WirePaymentCaptureLinesQuery value) {
        if (value == null) return;
        if (!value.hasCaptureId()) invalid(); text(value.getCaptureId(), 64);
        if (value.hasPage() && value.getPage() < 1 || value.hasSize() && (value.getSize() < 1 || value.getSize() > 100)) invalid();
    }
    @BeforeMapping default void required(WirePaymentLedgerLineDTO value) {
        if (value == null) return;
        if (!value.hasOrdinal() || !value.hasKind() || !value.hasChannelTransactionNo() || !value.hasMerchantPaymentNo()
                || !value.hasAmountMinor() || !value.hasCurrency() || !value.hasStatus() || !value.hasSourceAt()
                || !value.hasSourceId() || !value.hasEvidenceId() || !value.hasSourceVersion()
                || value.getOrdinal() < 1 || value.getAmountMinor() < 1 || value.getSourceVersion() < 0 || !"CNY".equals(value.getCurrency())) invalid();
        var kind = PaymentCaptureRpcFields.kind(value.getKind()); var status = PaymentCaptureRpcFields.status(value.getStatus());
        if ((kind == io.github.loadup.pay.payment.client.enums.PaymentLedgerKind.REFUND) != value.hasChannelRefundNo()
                || kind == io.github.loadup.pay.payment.client.enums.PaymentLedgerKind.PAYMENT
                    && status != io.github.loadup.pay.payment.client.enums.PaymentLedgerStatus.SUCCEEDED) invalid();
        text(value.getChannelTransactionNo(), 128); text(value.getMerchantPaymentNo(), 64);
        if (value.hasChannelRefundNo()) text(value.getChannelRefundNo(), 64);
        text(value.getSourceId(), 64); text(value.getEvidenceId(), 64); timestamp(value.getSourceAt());
    }
    @BeforeMapping default void required(WirePaymentCaptureDTO value) {
        if (value == null) return;
        if (!value.hasCaptureId() || !value.hasChannel() || !value.hasAccountScopeHash() || !value.hasBillDate()
                || !value.hasStatementProfile() || !value.hasSystemZone() || !value.hasState() || !value.hasVersion()
                || !value.hasNativeTaskId() || !value.hasNativeStatus() || !value.hasAttempts() || !value.hasMaxAttempts()) invalid();
        text(value.getCaptureId(), 64); text(value.getNativeTaskId(), 64);
        scope(value.getChannel(), value.getAccountScopeHash(), value.getBillDate(), value.getStatementProfile());
        try { if (!java.time.ZoneId.of(value.getSystemZone()).getId().equals(value.getSystemZone())) invalid(); }
        catch (java.time.DateTimeException malformed) { invalid(); }
        if (!Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED").contains(value.getNativeStatus())
                || value.getAttempts() < 0 || value.getMaxAttempts() < 1 || value.getAttempts() > value.getMaxAttempts()) invalid();
        var state = PaymentCaptureRpcFields.state(value.getState()); boolean initial = state == io.github.loadup.pay.payment.client.enums.PaymentCaptureState.INIT;
        boolean sealed = state == io.github.loadup.pay.payment.client.enums.PaymentCaptureState.SEALED;
        boolean blocked = state == io.github.loadup.pay.payment.client.enums.PaymentCaptureState.BLOCKED;
        if (value.getVersion() != (initial ? 0L : 1L) || initial == value.hasStartedAt() || initial == value.hasFinishedAt()
                || sealed != value.hasPaymentCount() || sealed != value.hasPaymentAmountMinor() || sealed != value.hasRefundCount()
                || sealed != value.hasRefundAmountMinor() || sealed != value.hasContentHash()
                || blocked != value.hasBlocker() || blocked != value.hasBlockerSubjectId()) invalid();
        if (!initial) {
            timestamp(value.getStartedAt()); timestamp(value.getFinishedAt());
            if (GrpcScalarMapper.offset(value.getFinishedAt()).isBefore(GrpcScalarMapper.offset(value.getStartedAt()))) invalid();
        }
        if (sealed) {
            hash(value.getContentHash());
            if (value.getPaymentCount() < 0 || value.getRefundCount() < 0 || value.getPaymentAmountMinor() < value.getPaymentCount()
                    || value.getRefundAmountMinor() < value.getRefundCount()
                    || (value.getPaymentCount() == 0) != (value.getPaymentAmountMinor() == 0)
                    || (value.getRefundCount() == 0) != (value.getRefundAmountMinor() == 0)) invalid();
            try { Math.addExact(value.getPaymentCount(), value.getRefundCount()); } catch (ArithmeticException overflow) { invalid(); }
        }
        if (blocked) { PaymentCaptureRpcFields.blocker(value.getBlocker()); text(value.getBlockerSubjectId(), 64); }
    }
    @BeforeMapping default void required(WirePaymentCapturePage value) {
        if (value == null) return;
        if (!value.hasTotalCount() || !value.hasPage() || !value.hasSize() || value.getTotalCount() < 0
                || value.getPage() < 1 || value.getSize() < 1 || value.getSize() > 100) invalid();
        long offset = (long) (value.getPage() - 1) * value.getSize();
        long expected = Math.min(value.getSize(), Math.max(0L, value.getTotalCount() - offset));
        if (value.getDataCount() != expected) invalid();
        for (var row : value.getDataList()) if (!row.hasOrdinal() || row.getOrdinal() != ++offset) invalid();
    }
    private void scope(String channel, String account, String date, String profile) {
        PaymentCaptureRpcFields.channel(channel); hash(account); PaymentCaptureRpcFields.date(date);
        if (!"wechat-trade-all-20260116".equals(profile)) invalid();
    }
    private void text(String value, int max) { if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) invalid(); }
    private void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) invalid(); }
    private void timestamp(com.google.protobuf.Timestamp value) {
        try { com.google.protobuf.util.Timestamps.checkValid(value); if (value.getNanos() % 1000 != 0) invalid(); }
        catch (IllegalArgumentException malformed) { invalid(); }
    }
    private void invalid() { throw new CommonException(INVALID_ARGUMENT); }
}
