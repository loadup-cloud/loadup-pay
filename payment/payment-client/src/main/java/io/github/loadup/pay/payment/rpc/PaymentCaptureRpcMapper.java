package io.github.loadup.pay.payment.rpc;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.result.PageInfo;
import io.github.loadup.commons.result.ResultCode;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus;
import io.github.loadup.pay.payment.rpc.PaymentCaptureRpcFields;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCapturePage;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureRetryCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentLedgerLineDTO;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(config=LoadUpMapStructConfig.class, componentModel="default", uses={PaymentCaptureRpcFields.class, RpcScalarMapper.class}, nullValueCheckStrategy=NullValueCheckStrategy.ALWAYS)
public interface PaymentCaptureRpcMapper {
    PaymentCaptureRpcMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(PaymentCaptureRpcMapper.class);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="captureId", source="captureId"), @Mapping(target="expectedTaskId", source="expectedTaskId")})
    public WirePaymentCaptureRetryCommand toWire(PaymentCaptureRetryCommand value);

    public PaymentCaptureRetryCommand fromWire(WirePaymentCaptureRetryCommand value);

    @BeforeMapping
    default public void required(WirePaymentCaptureRetryCommand value) {
        if (value == null) {
            return;
        }
        if (!value.hasCaptureId() || !value.hasExpectedTaskId()) {
            this.invalid();
        }
        this.text(value.getCaptureId(), 64);
        this.text(value.getExpectedTaskId(), 64);
    }

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="requestKey", source="requestKey"), @Mapping(target="channel", source="channel"), @Mapping(target="accountScopeHash", source="accountScopeHash"), @Mapping(target="billDate", source="billDate"), @Mapping(target="statementProfile", source="statementProfile")})
    public WirePaymentCaptureCreateCommand toWire(PaymentCaptureCreateCommand value);

    public PaymentCaptureCreateCommand fromWire(WirePaymentCaptureCreateCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mapping(target="id", source="id")
    public WirePaymentCaptureQuery toWire(IdQuery value);

    public IdQuery fromWire(WirePaymentCaptureQuery value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="captureId", source="captureId"), @Mapping(target="page", source="page"), @Mapping(target="size", source="size")})
    public WirePaymentCaptureLinesQuery toWire(PaymentCaptureLinesQuery value);

    public PaymentCaptureLinesQuery fromWire(WirePaymentCaptureLinesQuery value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="captureId", source="captureId"), @Mapping(target="channel", source="channel"), @Mapping(target="accountScopeHash", source="accountScopeHash"), @Mapping(target="billDate", source="billDate"), @Mapping(target="statementProfile", source="statementProfile"), @Mapping(target="systemZone", source="systemZone"), @Mapping(target="state", source="state"), @Mapping(target="startedAt", source="startedAt"), @Mapping(target="finishedAt", source="finishedAt"), @Mapping(target="paymentCount", source="paymentCount"), @Mapping(target="paymentAmountMinor", source="paymentAmountMinor"), @Mapping(target="refundCount", source="refundCount"), @Mapping(target="refundAmountMinor", source="refundAmountMinor"), @Mapping(target="contentHash", source="contentHash"), @Mapping(target="blocker", source="blocker"), @Mapping(target="blockerSubjectId", source="blockerSubjectId"), @Mapping(target="version", source="version"), @Mapping(target="nativeTaskId", source="nativeTaskId"), @Mapping(target="nativeStatus", source="nativeStatus"), @Mapping(target="attempts", source="attempts"), @Mapping(target="maxAttempts", source="maxAttempts")})
    public WirePaymentCaptureDTO toWire(PaymentCaptureDTO value);

    public PaymentCaptureDTO fromWire(WirePaymentCaptureDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="ordinal", source="ordinal"), @Mapping(target="kind", source="kind"), @Mapping(target="channelTransactionNo", source="channelTransactionNo"), @Mapping(target="channelRefundNo", source="channelRefundNo"), @Mapping(target="merchantPaymentNo", source="merchantPaymentNo"), @Mapping(target="amountMinor", source="amountMinor"), @Mapping(target="currency", source="currency"), @Mapping(target="status", source="status"), @Mapping(target="sourceAt", source="sourceAt"), @Mapping(target="sourceId", source="sourceId"), @Mapping(target="evidenceId", source="evidenceId"), @Mapping(target="sourceVersion", source="sourceVersion")})
    public WirePaymentLedgerLineDTO toWire(PaymentLedgerLineDTO value);

    public PaymentLedgerLineDTO fromWire(WirePaymentLedgerLineDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="totalCount", source="pageInfo.totalCount"), @Mapping(target="page", expression="java(Math.toIntExact(value.getPageInfo().pageIndex()))"), @Mapping(target="size", expression="java(Math.toIntExact(value.getPageInfo().pageSize()))")})
    public WirePaymentCapturePage toWire(PageDTO<PaymentLedgerLineDTO> value);

    @Mappings(value={@Mapping(target="data", source="dataList"), @Mapping(target="pageInfo", expression="java(metadata(value))")})
    public PageDTO<PaymentLedgerLineDTO> fromWire(WirePaymentCapturePage value);

    @Mappings(value={@Mapping(target="totalCount", source="totalCount"), @Mapping(target="pageIndex", source="page"), @Mapping(target="pageSize", source="size")})
    public PageInfo metadata(WirePaymentCapturePage value);

    public List<WirePaymentLedgerLineDTO> toWireLines(Collection<PaymentLedgerLineDTO> value);

    @AfterMapping
    default public void entries(PageDTO<PaymentLedgerLineDTO> value, @MappingTarget WirePaymentCapturePage.Builder target) {
        if (value != null && value.getData() != null) {
            target.addAllData(this.toWireLines(value.getData()));
        }
    }

    @BeforeMapping
    default public void required(WirePaymentCaptureCreateCommand value) {
        if (value == null) {
            return;
        }
        if (!(value.hasRequestKey() && value.hasChannel() && value.hasAccountScopeHash() && value.hasBillDate() && value.hasStatementProfile())) {
            this.invalid();
        }
        this.text(value.getRequestKey(), 128);
        this.scope(value.getChannel(), value.getAccountScopeHash(), value.getBillDate(), value.getStatementProfile());
    }

    @BeforeMapping
    default public void required(WirePaymentCaptureQuery value) {
        if (value != null) {
            if (!value.hasId()) {
                this.invalid();
            }
            this.text(value.getId(), 64);
        }
    }

    @BeforeMapping
    default public void required(WirePaymentCaptureLinesQuery value) {
        if (value == null) {
            return;
        }
        if (!value.hasCaptureId()) {
            this.invalid();
        }
        this.text(value.getCaptureId(), 64);
        if (value.hasPage() && value.getPage() < 1 || value.hasSize() && (value.getSize() < 1 || value.getSize() > 100)) {
            this.invalid();
        }
    }

    @BeforeMapping
    default public void required(WirePaymentLedgerLineDTO value) {
        if (value == null) {
            return;
        }
        if (!(value.hasOrdinal() && value.hasKind() && value.hasChannelTransactionNo() && value.hasMerchantPaymentNo() && value.hasAmountMinor() && value.hasCurrency() && value.hasStatus() && value.hasSourceAt() && value.hasSourceId() && value.hasEvidenceId() && value.hasSourceVersion() && value.getOrdinal() >= 1L && value.getAmountMinor() >= 1L && value.getSourceVersion() >= 0L && "CNY".equals(value.getCurrency()))) {
            this.invalid();
        }
        PaymentLedgerKind kind = PaymentCaptureRpcFields.kind(value.getKind());
        PaymentLedgerStatus status = PaymentCaptureRpcFields.status(value.getStatus());
        if (kind == PaymentLedgerKind.REFUND != value.hasChannelRefundNo() || kind == PaymentLedgerKind.PAYMENT && status != PaymentLedgerStatus.SUCCEEDED) {
            this.invalid();
        }
        this.text(value.getChannelTransactionNo(), 128);
        this.text(value.getMerchantPaymentNo(), 64);
        if (value.hasChannelRefundNo()) {
            this.text(value.getChannelRefundNo(), 64);
        }
        this.text(value.getSourceId(), 64);
        this.text(value.getEvidenceId(), 64);
        this.timestamp(value.getSourceAt());
    }

    @BeforeMapping
    default public void required(WirePaymentCaptureDTO value) {
        PaymentCaptureState state;
        if (value == null) {
            return;
        }
        if (!(value.hasCaptureId() && value.hasChannel() && value.hasAccountScopeHash() && value.hasBillDate() && value.hasStatementProfile() && value.hasSystemZone() && value.hasState() && value.hasVersion() && value.hasNativeTaskId() && value.hasNativeStatus() && value.hasAttempts() && value.hasMaxAttempts())) {
            this.invalid();
        }
        this.text(value.getCaptureId(), 64);
        this.text(value.getNativeTaskId(), 64);
        this.scope(value.getChannel(), value.getAccountScopeHash(), value.getBillDate(), value.getStatementProfile());
        try {
            if (!ZoneId.of(value.getSystemZone()).getId().equals(value.getSystemZone())) {
                this.invalid();
            }
        }
        catch (DateTimeException malformed) {
            this.invalid();
        }
        if (!Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED").contains(value.getNativeStatus()) || value.getAttempts() < 0 || value.getMaxAttempts() < 1 || value.getAttempts() > value.getMaxAttempts()) {
            this.invalid();
        }
        boolean initial = (state = PaymentCaptureRpcFields.state(value.getState())) == PaymentCaptureState.INIT;
        boolean sealed = state == PaymentCaptureState.SEALED;
        boolean blocked = state == PaymentCaptureState.BLOCKED;
        if (value.getVersion() != (initial ? 0L : 1L) || initial == value.hasStartedAt() || initial == value.hasFinishedAt() || sealed != value.hasPaymentCount() || sealed != value.hasPaymentAmountMinor() || sealed != value.hasRefundCount() || sealed != value.hasRefundAmountMinor() || sealed != value.hasContentHash() || blocked != value.hasBlocker() || blocked != value.hasBlockerSubjectId()) {
            this.invalid();
        }
        if (!initial) {
            this.timestamp(value.getStartedAt());
            this.timestamp(value.getFinishedAt());
            if (RpcScalarMapper.offset((Timestamp)value.getFinishedAt()).isBefore(RpcScalarMapper.offset((Timestamp)value.getStartedAt()))) {
                this.invalid();
            }
        }
        if (sealed) {
            this.hash(value.getContentHash());
            if (value.getPaymentCount() < 0L || value.getRefundCount() < 0L || value.getPaymentAmountMinor() < value.getPaymentCount() || value.getRefundAmountMinor() < value.getRefundCount() || value.getPaymentCount() == 0L != (value.getPaymentAmountMinor() == 0L) || value.getRefundCount() == 0L != (value.getRefundAmountMinor() == 0L)) {
                this.invalid();
            }
            try {
                Math.addExact(value.getPaymentCount(), value.getRefundCount());
            }
            catch (ArithmeticException overflow) {
                this.invalid();
            }
        }
        if (blocked) {
            PaymentCaptureRpcFields.blocker(value.getBlocker());
            this.text(value.getBlockerSubjectId(), 64);
        }
    }

    @BeforeMapping
    default public void required(WirePaymentCapturePage value) {
        if (value == null) {
            return;
        }
        if (!(value.hasTotalCount() && value.hasPage() && value.hasSize() && value.getTotalCount() >= 0L && value.getPage() >= 1 && value.getSize() >= 1 && value.getSize() <= 100)) {
            this.invalid();
        }
        long offset = (long)(value.getPage() - 1) * (long)value.getSize();
        long expected = Math.min((long)value.getSize(), Math.max(0L, value.getTotalCount() - offset));
        if ((long)value.getDataCount() != expected) {
            this.invalid();
        }
        for (WirePaymentLedgerLineDTO row : value.getDataList()) {
            if (row.hasOrdinal() && row.getOrdinal() == ++offset) continue;
            this.invalid();
        }
    }

    private void scope(String channel, String account, String date, String profile) {
        PaymentCaptureRpcFields.channel(channel);
        this.hash(account);
        PaymentCaptureRpcFields.date(date);
        if (!"wechat-trade-all-20260116".equals(profile)) {
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

    private void timestamp(Timestamp value) {
        try {
            Timestamps.checkValid((Timestamp)value);
            if (value.getNanos() % 1000 != 0) {
                this.invalid();
            }
        }
        catch (IllegalArgumentException malformed) {
            this.invalid();
        }
    }

    private void invalid() {
        throw new CommonException((ResultCode)PaymentResultCodeEnum.INVALID_ARGUMENT);
    }
}
