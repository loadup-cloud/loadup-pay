package io.github.loadup.pay.customer.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.FailureResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class CustomerHttpContractTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    private final TypeReference<SuccessResponse<MerchantEligibilityDTO>> type = new TypeReference<>() {
    };

    @Test
    void preservesGenericDataAndFailureCodesWithoutTrustingTheEnvelopeClass() {
        var data = new MerchantEligibilityDTO(false,
                CustomerResultCodeEnum.MERCHANT_INACTIVE.getCode(), null, 0, null, null);
        var success = json.readValue(json.writeValueAsString(SuccessResponse.of(data)), type);
        assertThat(success.data()).isEqualTo(data);
        var failure = json.readValue(json.writeValueAsString(
                FailureResponse.of(CustomerResultCodeEnum.NOT_FOUND, "synthetic-trace")), type);
        assertThat(failure.result().getCode()).isEqualTo(CustomerResultCodeEnum.NOT_FOUND.getCode());
        assertThat(failure.result().getStatus()).isEqualTo(CustomerResultCodeEnum.NOT_FOUND.getStatus());
        assertThat(failure.data()).isNull();
    }
}
