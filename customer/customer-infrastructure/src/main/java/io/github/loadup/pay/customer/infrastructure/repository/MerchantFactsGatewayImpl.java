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
package io.github.loadup.pay.customer.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.modules.merchant.infrastructure.dataobject.MerchantDO;
import io.github.loadup.modules.merchant.infrastructure.mapper.MerchantDOMapper;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.domain.gateway.MerchantFactsGateway;
import io.github.loadup.pay.customer.domain.model.MerchantFacts;
import io.github.loadup.pay.customer.infrastructure.converter.MerchantFactsConverter;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MerchantFactsGatewayImpl implements MerchantFactsGateway {
    private final MerchantDOMapper mapper;
    private final MerchantFactsConverter converter;

    public MerchantFactsGatewayImpl(MerchantDOMapper mapper, MerchantFactsConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    private QueryWrapper scope(String tenant, String id) {
        return QueryWrapper.create()
                .where(MerchantDO::getTenantId)
                .eq(tenant)
                .and(MerchantDO::getDeleted)
                .eq(0)
                .and(MerchantDO::getId)
                .eq(id);
    }

    @Override
    public Optional<MerchantFacts> find(String tenant, String id) {
        return Optional.ofNullable(mapper.selectOneByQuery(scope(tenant, id))).map(converter::toFacts);
    }

    @Override
    public MerchantFacts lock(String tenant, String id) {
        return Optional.ofNullable(mapper.selectOneByQuery(scope(tenant, id).forUpdate()))
                .map(converter::toFacts)
                .orElseThrow(() -> new CommonException(CustomerResultCodeEnum.NOT_FOUND));
    }
}
