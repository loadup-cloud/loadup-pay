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
import io.github.loadup.pay.payment.app.port.RoutingProfileCodec;
import io.github.loadup.pay.payment.domain.gateway.RoutingProfileGateway;
import io.github.loadup.pay.payment.domain.model.RoutingProfile;
import io.github.loadup.pay.payment.infrastructure.converter.RoutingProfileStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RoutingProfileDO;
import io.github.loadup.pay.payment.infrastructure.mapper.RoutingProfileDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.ROUTING_PROFILE_DO;

@Repository
public class RoutingProfileGatewayImpl implements RoutingProfileGateway {
    private final RoutingProfileDOMapper mapper;
    private final RoutingProfileStorageConverter converter;
    private final RoutingProfileCodec codec;

    public RoutingProfileGatewayImpl(RoutingProfileDOMapper mapper, RoutingProfileStorageConverter converter,
                                     RoutingProfileCodec codec) {
        this.mapper = mapper;
        this.converter = converter;
        this.codec = codec;
    }

    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create().where(ROUTING_PROFILE_DO.TENANT_ID.eq(tenant)).and(ROUTING_PROFILE_DO.DELETED.eq(0));
    }

    @Override
    public Optional<RoutingProfile> find(String tenant, String ref) {
        return decode(mapper.selectOneByQuery(scope(tenant).and(ROUTING_PROFILE_DO.PROFILE_REF.eq(ref))));
    }

    @Override
    public Optional<RoutingProfile> findRequest(String tenant, String caller, String key) {
        return decode(mapper.selectOneByQuery(scope(tenant).and(ROUTING_PROFILE_DO.CALLER_SERVICE_ID.eq(caller))
                .and(ROUTING_PROFILE_DO.REQUEST_KEY.eq(key))));
    }

    private Optional<RoutingProfile> decode(RoutingProfileDO row) {
        if (row == null) return Optional.empty();
        try {
            var spec = codec.decode(row.getSpecPayload(), row.getContentHash());
            if (!spec.profileRef().equals(row.getProfileRef()) || !spec.payeeMerchantId().equals(row.getPayeeMerchantId())
                    || !codec.requestDigest(row.getTenantId(), row.getCallerServiceId(), row.getRequestKey(),
                    row.getContentHash()).equals(row.getRequestDigest())) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toDomain(row, spec));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, RoutingProfile profile) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        String payload = codec.encode(profile.spec());
        if (!codec.checksum(payload).equals(profile.contentHash())
                || !codec.requestDigest(tenant, profile.callerServiceId(), profile.requestKey(), profile.contentHash())
                .equals(profile.requestDigest())) throw new CommonException(SNAPSHOT_INVALID);
        if (mapper.insert(converter.toDO(tenant, profile, payload)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
}
