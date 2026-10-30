package io.github.loadup.pay.product.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.rpc.CustomerRpcMapper;
import io.github.loadup.pay.customer.rpc.CustomerRpcMapperImpl;
import io.github.loadup.pay.customer.rpc.proto.CustomerRpc;
import io.github.loadup.pay.product.app.security.ProductActor;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class CustomerEligibilityRpcLookupTest {
    private static final String TOKEN = "aGVhZGVy.eyJzdWIiOiJtZXJjaGFudCJ9.c2lnbmF0dXJl";

    private final CustomerRpc customerRpc = mock(CustomerRpc.class);
    private final CustomerRpcMapper customerRpcMapper = new CustomerRpcMapperImpl();
    private final ProductActor actor =
            new ProductActor("tenant", "MERCHANT_APP", "app", "merchant", TOKEN, Set.of());
    private final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    private final CustomerEligibilityRpcLookup lookup = new CustomerEligibilityRpcLookup(customerRpcMapper, () -> actor);

    private static RpcException remoteFailure(String code) {
        return new RpcException(RpcException.BIZ_EXCEPTION, "loadup-result-code=" + code);
    }

    @BeforeEach
    void inject() {
        ReflectionTestUtils.setField(lookup, "customerRpc", customerRpc);
    }

    private void respond(MerchantEligibilityDTO value) {
        doAnswer(invocation -> customerRpcMapper.toWireMerchantEligibilityDTO(value))
                .when(customerRpc).authorize(any());
    }

    private void fail(String code) {
        doAnswer(invocation -> {
            throw remoteFailure(code);
        }).when(customerRpc).authorize(any());
    }

    @Test
    void acceptsOnlyFactsBoundToTheExpectedTenantAndPayee() {
        respond(new MerchantEligibilityDTO(
                true,
                "ALLOWED",
                new MerchantProfileDTO("merchant", "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true),
                1,
                "grant",
                1L));
        assertTrue(lookup.authorizeNewOrder("tenant", "merchant").allowed());
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "different-merchant"));
    }

    @Test
    void wrongTenantProfileCannotBeUsedAsAuthoritativeFacts() {
        respond(new MerchantEligibilityDTO(
                true,
                "ALLOWED",
                new MerchantProfileDTO("merchant", "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true),
                1,
                "grant",
                1L));
        var error = assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
        assertSame(ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE, error.getResultCode());
    }

    @Test
    void authenticationFailureIsNotConvertedIntoABusinessDenial() {
        fail(CustomerResultCodeEnum.ACTION_FORBIDDEN.getCode());
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
    }

    @Test
    void verifiedNotFoundAndExplicitIneligibilityRemainBusinessDenials() {
        fail(CustomerResultCodeEnum.NOT_FOUND.getCode());
        assertEquals(
                CustomerResultCodeEnum.NOT_FOUND.getCode(),
                lookup.authorizeNewOrder("tenant", "merchant").reasonCode());
        respond(new MerchantEligibilityDTO(
                false, CustomerResultCodeEnum.MERCHANT_INACTIVE.getCode(), null, 0, null, null));
        assertFalse(lookup.authorizeNewOrder("tenant", "merchant").allowed());
    }
}
