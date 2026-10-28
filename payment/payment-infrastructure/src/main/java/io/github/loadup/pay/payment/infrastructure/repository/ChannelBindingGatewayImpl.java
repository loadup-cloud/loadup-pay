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
package io.github.loadup.pay.payment.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.payment.app.port.ChannelBindingCodec;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingManagementGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingChangeReceipt;
import io.github.loadup.pay.payment.domain.model.ChannelBindingState;
import io.github.loadup.pay.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.pay.payment.domain.model.RoutableChannelBinding;
import io.github.loadup.pay.payment.domain.model.StoredChannelBindingRevision;
import io.github.loadup.pay.payment.infrastructure.converter.ChannelBindingStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelMerchantBindingDO;
import io.github.loadup.pay.payment.infrastructure.mapper.ChannelBindingChangeReceiptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.ChannelBindingRevisionDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.ChannelMerchantBindingDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.CHANNEL_BINDING_CHANGE_RECEIPT_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.CHANNEL_BINDING_REVISION_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.CHANNEL_MERCHANT_BINDING_DO;

@Repository
public class ChannelBindingGatewayImpl implements ChannelBindingManagementGateway, ChannelBindingGateway {
    private final ChannelMerchantBindingDOMapper heads;
    private final ChannelBindingRevisionDOMapper revisions;
    private final ChannelBindingChangeReceiptDOMapper receipts;
    private final ChannelBindingStorageConverter converter;
    private final ChannelBindingCodec codec;

    public ChannelBindingGatewayImpl(ChannelMerchantBindingDOMapper heads, ChannelBindingRevisionDOMapper revisions,
                                     ChannelBindingChangeReceiptDOMapper receipts, ChannelBindingStorageConverter converter, ChannelBindingCodec codec) {
        this.heads = heads;
        this.revisions = revisions;
        this.receipts = receipts;
        this.converter = converter;
        this.codec = codec;
    }

    private QueryWrapper headScope(String tenant) {
        return QueryWrapper.create().where(CHANNEL_MERCHANT_BINDING_DO.TENANT_ID.eq(tenant))
                .and(CHANNEL_MERCHANT_BINDING_DO.DELETED.eq(0));
    }

    private QueryWrapper headKey(String tenant, String payee, String code) {
        return headScope(tenant).and(CHANNEL_MERCHANT_BINDING_DO.PAYEE_MERCHANT_ID.eq(payee))
                .and(CHANNEL_MERCHANT_BINDING_DO.BINDING_CODE.eq(code));
    }

    @Override
    public Optional<ChannelMerchantBinding> find(String tenant, String payee, String code) {
        return head(heads.selectOneByQuery(headKey(tenant, payee, code)));
    }

    @Override
    public Optional<ChannelMerchantBinding> lock(String tenant, String payee, String code) {
        requireTransaction();
        return head(heads.selectOneByQuery(headKey(tenant, payee, code).forUpdate()));
    }

    private Optional<ChannelMerchantBinding> head(ChannelMerchantBindingDO row) {
        try {
            return Optional.ofNullable(row).map(converter::toHead);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<StoredChannelBindingRevision> revision(String tenant, String revisionId) {
        if (revisionId == null) throw new CommonException(SNAPSHOT_INVALID);
        var row = revisions.selectOneByQuery(QueryWrapper.create()
                .where(CHANNEL_BINDING_REVISION_DO.TENANT_ID.eq(tenant)).and(CHANNEL_BINDING_REVISION_DO.DELETED.eq(0))
                .and(CHANNEL_BINDING_REVISION_DO.ID.eq(revisionId)));
        if (row == null) return Optional.empty();
        try {
            var identity = codec.decodeIdentity(row.getIdentityPayload(), row.getIdentityHash());
            var capabilities = EnumSet.noneOf(PaymentMethod.class);
            capability(capabilities, PaymentMethod.PAYMENT_CODE, row.getPaymentCodeEnabled());
            capability(capabilities, PaymentMethod.JSAPI, row.getJsapiEnabled());
            capability(capabilities, PaymentMethod.H5, row.getH5Enabled());
            var binding = converter.toBinding(row, identity, capabilities);
            if (!binding.accountScopeHash().equals(codec.accountScopeHash(binding.channel(), identity)))
                throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toRevision(row, binding));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void capability(Set<PaymentMethod> methods, PaymentMethod method, Integer enabled) {
        if (enabled == null || enabled < 0 || enabled > 1) throw new CommonException(SNAPSHOT_INVALID);
        if (enabled == 1) methods.add(method);
    }

    @Override
    public Optional<ChannelBindingRevision> findRevision(String tenant, String revisionId) {
        return revision(tenant, revisionId).map(StoredChannelBindingRevision::binding);
    }

    @Override
    public List<RoutableChannelBinding> lockActive(String tenant, String payee, Channel channel,
                                                   Set<AccessMode> allowedModes, PaymentMethod method) {
        requireTransaction();
        if (allowedModes.isEmpty()) return List.of();
        var rows = heads.selectListByQuery(headScope(tenant)
                .and(CHANNEL_MERCHANT_BINDING_DO.PAYEE_MERCHANT_ID.eq(payee))
                .and(CHANNEL_MERCHANT_BINDING_DO.CHANNEL.eq(channel.name()))
                .and(CHANNEL_MERCHANT_BINDING_DO.ACCESS_MODE.in(allowedModes.stream().map(Enum::name).toList()))
                .and(CHANNEL_MERCHANT_BINDING_DO.STATE.eq("ACTIVE"))
                .orderBy(CHANNEL_MERCHANT_BINDING_DO.ID.asc()).forUpdate());
        var candidates = new ArrayList<RoutableChannelBinding>();
        for (var row : rows) {
            var head = head(row).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var revision = revision(tenant, head.currentRevisionId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            final RoutableChannelBinding candidate;
            try {
                candidate = new RoutableChannelBinding(head, revision);
            } catch (RuntimeException corrupt) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            if (revision.binding().capabilities().contains(method)) candidates.add(candidate);
        }
        candidates.sort(Comparator.comparingInt((RoutableChannelBinding candidate) -> candidate.head().priority())
                .thenComparing(candidate -> candidate.head().id()));
        return List.copyOf(candidates);
    }

    @Override
    public Optional<ChannelBindingChangeReceipt> receipt(String tenant, String bindingId, String key) {
        var row = receipts.selectOneByQuery(QueryWrapper.create()
                .where(CHANNEL_BINDING_CHANGE_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(CHANNEL_BINDING_CHANGE_RECEIPT_DO.DELETED.eq(0))
                .and(CHANNEL_BINDING_CHANGE_RECEIPT_DO.BINDING_ID.eq(bindingId))
                .and(CHANNEL_BINDING_CHANGE_RECEIPT_DO.REQUEST_KEY.eq(key)));
        try {
            return Optional.ofNullable(row).map(converter::toReceipt);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertHead(String tenant, ChannelMerchantBinding head) {
        requireTransaction();
        if (head.state() != ChannelBindingState.ACTIVE || head.rowVersion() != 1 || head.currentRevision() != 1)
            throw new CommonException(INVALID_STATE);
        if (heads.insert(converter.toHeadDO(tenant, head)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateHead(String tenant, ChannelMerchantBinding head, long expectedVersion) {
        requireTransaction();
        if (head.rowVersion() != Math.incrementExact(expectedVersion) || head.state() == ChannelBindingState.INIT)
            throw new CommonException(INVALID_STATE);
        int changed = heads.updateByQuery(converter.toHeadPatch(head), true,
                headKey(tenant, head.payeeMerchantId(), head.bindingCode())
                        .and(CHANNEL_MERCHANT_BINDING_DO.ID.eq(head.id()))
                        .and(CHANNEL_MERCHANT_BINDING_DO.CHANNEL.eq(head.channel().name()))
                        .and(CHANNEL_MERCHANT_BINDING_DO.ACCESS_MODE.eq(head.accessMode().name()))
                        .and(CHANNEL_MERCHANT_BINDING_DO.ROW_VERSION.eq(expectedVersion)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void insertRevision(String tenant, StoredChannelBindingRevision value) {
        requireTransaction();
        String payload = codec.encodeIdentity(value.binding().identity());
        if (!codec.checksum(payload).equals(value.identityHash())) throw new CommonException(SNAPSHOT_INVALID);
        if (revisions.insert(converter.toRevisionDO(tenant, value, payload)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void insertReceipt(String tenant, ChannelBindingChangeReceipt receipt) {
        requireTransaction();
        if (receipts.insert(converter.toReceiptDO(tenant, receipt)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
