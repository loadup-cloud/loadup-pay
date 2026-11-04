/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.merchantcenter.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.merchantcenter.app.converter.MerchantConverter;
import io.github.loadup.merchantcenter.app.converter.MerchantConverterImpl;
import io.github.loadup.merchantcenter.domain.model.Merchant;
import io.github.loadup.merchantcenter.domain.model.MerchantBasicInfo;
import io.github.loadup.merchantcenter.domain.model.MerchantStatus;
import io.github.loadup.merchantcenter.domain.model.MerchantType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MerchantBasicInfoTest {
    @Test
    void nullPrivateFieldsPreserveDataAndEmptyValuesClearIt() {
        new ApplicationContextRunner().withBean(MerchantConverterImpl.class).run(context -> {
            MerchantConverter converter = context.getBean(MerchantConverter.class);
            var previous = info("M001", "13800138000");
            assertThat(converter.merge(info("M001", null), previous).contactPhone())
                    .isEqualTo("13800138000");
            assertThat(converter.merge(info("M001", ""), previous).contactPhone())
                    .isEmpty();
        });
    }

    @Test
    void rejectsCodeMutationAndInvalidBasicValues() {
        var at = LocalDateTime.of(2026, 10, 10, 0, 0);
        var merchant = new Merchant("id", info("M001", null), MerchantStatus.ACTIVE, 1, "actor", "actor", at, at);
        assertThatThrownBy(() -> merchant.update(info("M002", null), "actor", at))
                .isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> info("M001", "138****8000")).isInstanceOf(CommonException.class);
        assertThat(merchant.changeStatus(MerchantStatus.INACTIVE, "actor", at).rowVersion())
                .isEqualTo(2);
    }

    private MerchantBasicInfo info(String code, String phone) {
        return new MerchantBasicInfo(
                code,
                "Retail",
                null,
                MerchantType.ENTERPRISE,
                "RETAIL",
                "CN",
                null,
                null,
                null,
                null,
                null,
                phone,
                null);
    }
}
