package io.github.loadup.acquire.client.dto.payment;


import io.github.loadup.commons.json.ToStringAsJson;

public record NoActionDTO() implements PaymentActionDataDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
