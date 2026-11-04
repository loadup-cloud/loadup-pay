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
package io.github.loadup.merchantcenter.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.FailureResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO;
import io.github.loadup.merchantcenter.client.error.CustomerResultCodeEnum;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class CustomerHttpContractTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    private final TypeReference<SuccessResponse<MerchantEligibilityDTO>> type = new TypeReference<>() {};

    @Test
    void preservesGenericDataAndFailureCodesWithoutTrustingTheEnvelopeClass() {
        var data = new MerchantEligibilityDTO(
                false, CustomerResultCodeEnum.MERCHANT_INACTIVE.getCode(), null, 0, null, null);
        var success = json.readValue(json.writeValueAsString(SuccessResponse.of(data)), type);
        assertThat(success.data()).isEqualTo(data);
        var failure = json.readValue(
                json.writeValueAsString(FailureResponse.of(CustomerResultCodeEnum.NOT_FOUND, "synthetic-trace")), type);
        assertThat(failure.result().getCode()).isEqualTo(CustomerResultCodeEnum.NOT_FOUND.getCode());
        assertThat(failure.result().getStatus()).isEqualTo(CustomerResultCodeEnum.NOT_FOUND.getStatus());
        assertThat(failure.data()).isNull();
    }
}
