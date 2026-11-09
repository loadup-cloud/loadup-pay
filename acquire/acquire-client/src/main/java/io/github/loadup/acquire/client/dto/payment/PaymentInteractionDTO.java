package io.github.loadup.acquire.client.dto.payment;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NoInteractionDTO.class, name = "NONE"),
    @JsonSubTypes.Type(value = PaymentCodeInteractionDTO.class, name = "PAYMENT_CODE"),
    @JsonSubTypes.Type(value = ChannelIdentityInteractionDTO.class, name = "CHANNEL_IDENTITY"),
    @JsonSubTypes.Type(value = H5SceneInteractionDTO.class, name = "H5_SCENE")
})
public sealed interface PaymentInteractionDTO
        permits NoInteractionDTO, PaymentCodeInteractionDTO, ChannelIdentityInteractionDTO, H5SceneInteractionDTO {}
