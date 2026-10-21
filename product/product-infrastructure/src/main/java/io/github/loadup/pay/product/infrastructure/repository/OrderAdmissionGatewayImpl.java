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
package io.github.loadup.pay.product.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import io.github.loadup.pay.product.domain.gateway.OrderAdmissionGateway;
import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;
import io.github.loadup.pay.product.infrastructure.converter.AdmissionStorageConverter;
import io.github.loadup.pay.product.infrastructure.dataobject.OrderAdmissionDO;
import io.github.loadup.pay.product.infrastructure.mapper.OrderAdmissionDOMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrderAdmissionGatewayImpl implements OrderAdmissionGateway {
    private final OrderAdmissionDOMapper mapper;
    private final AdmissionStorageConverter converter;

    public OrderAdmissionGatewayImpl(OrderAdmissionDOMapper mapper, AdmissionStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create()
                .where(OrderAdmissionDO::getTenantId)
                .eq(tenant)
                .and(OrderAdmissionDO::getDeleted)
                .eq(0);
    }

    @Override
    public Optional<StoredOrderAdmission> findByOrder(String tenant, String order) {
        return stored(mapper.selectOneByQuery(
                scope(tenant).and(OrderAdmissionDO::getOrderId).eq(order)));
    }

    @Override
    public Optional<StoredOrderAdmission> findById(String tenant, String id) {
        return stored(mapper.selectOneByQuery(
                scope(tenant).and(OrderAdmissionDO::getId).eq(id)));
    }

    private Optional<StoredOrderAdmission> stored(OrderAdmissionDO row) {
        try {
            return Optional.ofNullable(row).map(converter::toStored);
        } catch (IllegalArgumentException | NullPointerException corrupted) {
            throw new CommonException(ProductResultCodeEnum.SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, StoredOrderAdmission value) {
        // DuplicateKeyException must escape the transaction; the application reads the winner after rollback.
        mapper.insert(converter.toDO(tenant, value));
    }
}
