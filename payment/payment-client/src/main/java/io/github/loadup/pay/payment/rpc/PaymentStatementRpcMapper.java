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
import io.github.loadup.components.rpc.grpc.GrpcScalarMapper;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementQuery;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

@Mapper(config = LoadUpMapStructConfig.class, uses = {PaymentCaptureRpcFields.class, PaymentStatementRpcBytes.class, GrpcScalarMapper.class},
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface PaymentStatementRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "bindingRevisionId", source = "bindingRevisionId")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "accountScopeHash", source = "accountScopeHash")
    @Mapping(target = "billDate", source = "billDate")
    @Mapping(target = "statementProfile", source = "statementProfile")
    @Mapping(target = "maxBytes", source = "maxBytes")
    WirePaymentStatementQuery toWire(PaymentStatementQuery value);

    PaymentStatementQuery fromWire(WirePaymentStatementQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "bindingRevisionId", source = "bindingRevisionId")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "accountScopeHash", source = "accountScopeHash")
    @Mapping(target = "billDate", source = "billDate")
    @Mapping(target = "statementProfile", source = "statementProfile")
    @Mapping(target = "format", source = "format")
    @Mapping(target = "requestSha256", source = "requestSha256")
    @Mapping(target = "evidenceSha256", source = "evidenceSha256")
    @Mapping(target = "officialHashType", source = "officialHashType")
    @Mapping(target = "officialHash", source = "officialHash")
    @Mapping(target = "contentSha256", source = "contentSha256")
    @Mapping(target = "receivedAt", source = "receivedAt")
    @Mapping(target = "downloadedAt", source = "downloadedAt")
    @Mapping(target = "evidence", source = "evidence")
    @Mapping(target = "content", source = "content")
    WirePaymentStatementDTO toWire(PaymentStatementDTO value);

    PaymentStatementDTO fromWire(WirePaymentStatementDTO value);

    @BeforeMapping
    default void required(WirePaymentStatementQuery value) {
        if (value == null) return;
        if (!value.hasBindingRevisionId() || !value.hasChannel() || !value.hasAccountScopeHash() || !value.hasBillDate()
                || !value.hasStatementProfile() || !value.hasMaxBytes() || value.getMaxBytes() < 1 || value.getMaxBytes() > 16777216)
            invalid();
        scope(value.getBindingRevisionId(), value.getChannel(), value.getAccountScopeHash(), value.getBillDate(), value.getStatementProfile());
    }

    @BeforeMapping
    default void required(WirePaymentStatementDTO value) {
        if (value == null) return;
        if (!value.hasBindingRevisionId() || !value.hasChannel() || !value.hasAccountScopeHash() || !value.hasBillDate()
                || !value.hasStatementProfile() || !value.hasFormat() || !value.hasRequestSha256() || !value.hasEvidenceSha256()
                || !value.hasOfficialHashType() || !value.hasOfficialHash() || !value.hasContentSha256() || !value.hasReceivedAt()
                || !value.hasDownloadedAt() || !value.hasEvidence() || !value.hasContent()
                || value.getEvidence().isEmpty() || value.getEvidence().size() > 32768 || value.getContent().isEmpty()
                || value.getContent().size() > 16777216 || !"wechat-tradebill-grant-v1".equals(value.getFormat())
                || !"SHA1".equals(value.getOfficialHashType()) || !value.getOfficialHash().matches("[0-9a-f]{40}"))
            invalid();
        scope(value.getBindingRevisionId(), value.getChannel(), value.getAccountScopeHash(), value.getBillDate(), value.getStatementProfile());
        hash(value.getRequestSha256());
        hash(value.getEvidenceSha256());
        hash(value.getContentSha256());
        timestamp(value.getReceivedAt());
        timestamp(value.getDownloadedAt());
        if (GrpcScalarMapper.offset(value.getDownloadedAt()).isBefore(GrpcScalarMapper.offset(value.getReceivedAt())))
            invalid();
    }

    private void scope(String revision, String channel, String account, String date, String profile) {
        if (revision == null || revision.isBlank() || revision.length() > 64 || revision.chars().anyMatch(Character::isISOControl)
                || !"wechat-trade-all-20260116".equals(profile)) invalid();
        PaymentCaptureRpcFields.channel(channel);
        PaymentCaptureRpcFields.date(date);
        hash(account);
    }

    private void hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) invalid();
    }

    private void timestamp(com.google.protobuf.Timestamp value) {
        try {
            com.google.protobuf.util.Timestamps.checkValid(value);
            if (value.getNanos() % 1000 != 0) invalid();
        } catch (IllegalArgumentException invalid) {
            invalid();
        }
    }

    private void invalid() {
        throw new CommonException(INVALID_ARGUMENT);
    }
}
