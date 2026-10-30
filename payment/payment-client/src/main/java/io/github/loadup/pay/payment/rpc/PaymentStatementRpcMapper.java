package io.github.loadup.pay.payment.rpc;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.result.ResultCode;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementQuery;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(config = LoadUpMapStructConfig.class, uses = {PaymentCaptureRpcFields.class, PaymentStatementRpcBytes.class, RpcScalarMapper.class}, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface PaymentStatementRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "bindingRevisionId", source = "bindingRevisionId"), @Mapping(target = "channel", source = "channel"), @Mapping(target = "accountScopeHash", source = "accountScopeHash"), @Mapping(target = "billDate", source = "billDate"), @Mapping(target = "statementProfile", source = "statementProfile"), @Mapping(target = "maxBytes", source = "maxBytes")})
    public WirePaymentStatementQuery toWire(PaymentStatementQuery value);

    public PaymentStatementQuery fromWire(WirePaymentStatementQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "bindingRevisionId", source = "bindingRevisionId"), @Mapping(target = "channel", source = "channel"), @Mapping(target = "accountScopeHash", source = "accountScopeHash"), @Mapping(target = "billDate", source = "billDate"), @Mapping(target = "statementProfile", source = "statementProfile"), @Mapping(target = "format", source = "format"), @Mapping(target = "requestSha256", source = "requestSha256"), @Mapping(target = "evidenceSha256", source = "evidenceSha256"), @Mapping(target = "officialHashType", source = "officialHashType"), @Mapping(target = "officialHash", source = "officialHash"), @Mapping(target = "contentSha256", source = "contentSha256"), @Mapping(target = "receivedAt", source = "receivedAt"), @Mapping(target = "downloadedAt", source = "downloadedAt"), @Mapping(target = "evidence", source = "evidence"), @Mapping(target = "content", source = "content")})
    public WirePaymentStatementDTO toWire(PaymentStatementDTO value);

    public PaymentStatementDTO fromWire(WirePaymentStatementDTO value);

    @BeforeMapping
    default public void required(WirePaymentStatementQuery value) {
        if (value == null) {
            return;
        }
        if (!(value.hasBindingRevisionId() && value.hasChannel() && value.hasAccountScopeHash() && value.hasBillDate() && value.hasStatementProfile() && value.hasMaxBytes() && value.getMaxBytes() >= 1 && value.getMaxBytes() <= 0x1000000)) {
            this.invalid();
        }
        this.scope(value.getBindingRevisionId(), value.getChannel(), value.getAccountScopeHash(), value.getBillDate(), value.getStatementProfile());
    }

    @BeforeMapping
    default public void required(WirePaymentStatementDTO value) {
        if (value == null) {
            return;
        }
        if (!(value.hasBindingRevisionId() && value.hasChannel() && value.hasAccountScopeHash() && value.hasBillDate() && value.hasStatementProfile() && value.hasFormat() && value.hasRequestSha256() && value.hasEvidenceSha256() && value.hasOfficialHashType() && value.hasOfficialHash() && value.hasContentSha256() && value.hasReceivedAt() && value.hasDownloadedAt() && value.hasEvidence() && value.hasContent() && !value.getEvidence().isEmpty() && value.getEvidence().size() <= 32768 && !value.getContent().isEmpty() && value.getContent().size() <= 0x1000000 && "wechat-tradebill-grant-v1".equals(value.getFormat()) && "SHA1".equals(value.getOfficialHashType()) && value.getOfficialHash().matches("[0-9a-f]{40}"))) {
            this.invalid();
        }
        this.scope(value.getBindingRevisionId(), value.getChannel(), value.getAccountScopeHash(), value.getBillDate(), value.getStatementProfile());
        this.hash(value.getRequestSha256());
        this.hash(value.getEvidenceSha256());
        this.hash(value.getContentSha256());
        this.timestamp(value.getReceivedAt());
        this.timestamp(value.getDownloadedAt());
        if (RpcScalarMapper.offset((Timestamp) value.getDownloadedAt()).isBefore(RpcScalarMapper.offset((Timestamp) value.getReceivedAt()))) {
            this.invalid();
        }
    }

    private void scope(String revision, String channel, String account, String date, String profile) {
        if (revision == null || revision.isBlank() || revision.length() > 64 || revision.chars().anyMatch(Character::isISOControl) || !"wechat-trade-all-20260116".equals(profile)) {
            this.invalid();
        }
        PaymentCaptureRpcFields.channel(channel);
        PaymentCaptureRpcFields.date(date);
        this.hash(account);
    }

    private void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            this.invalid();
        }
    }

    private void timestamp(Timestamp value) {
        try {
            Timestamps.checkValid((Timestamp) value);
            if (value.getNanos() % 1000 != 0) {
                this.invalid();
            }
        } catch (IllegalArgumentException invalid) {
            this.invalid();
        }
    }

    private void invalid() {
        throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
    }
}
