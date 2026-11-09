package io.github.loadup.limitcenter.app.model.payment.dto;


import io.github.loadup.commons.json.ToStringAsJson;

public record NoActionDTO() implements PaymentActionDataDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
