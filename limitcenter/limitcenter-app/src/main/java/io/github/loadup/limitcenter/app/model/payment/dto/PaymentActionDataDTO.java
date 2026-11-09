package io.github.loadup.limitcenter.app.model.payment.dto;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NoActionDTO.class, name = "NONE"),
    @JsonSubTypes.Type(value = WaitActionDTO.class, name = "WAIT"),
    @JsonSubTypes.Type(value = RedirectActionDTO.class, name = "REDIRECT"),
    @JsonSubTypes.Type(value = WechatJsapiActionDTO.class, name = "WECHAT_JSAPI"),
    @JsonSubTypes.Type(value = AlipayJsapiActionDTO.class, name = "ALIPAY_JSAPI")
})
public sealed interface PaymentActionDataDTO
        permits NoActionDTO, WaitActionDTO, RedirectActionDTO, WechatJsapiActionDTO, AlipayJsapiActionDTO {}
