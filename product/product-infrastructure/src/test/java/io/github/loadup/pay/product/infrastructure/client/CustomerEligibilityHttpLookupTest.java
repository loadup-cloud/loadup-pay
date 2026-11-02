package io.github.loadup.pay.product.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.client.http.CustomerHttpService;
import io.github.loadup.pay.product.app.security.ProductActor;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CustomerEligibilityHttpLookupTest {
    private static final String TOKEN = "synthetic-customer-token";
    private final CustomerHttpService http = mock(CustomerHttpService.class);
    private final ProductActor actor = new ProductActor("tenant", "MERCHANT_APP", "app", "merchant", TOKEN, Set.of());
    private final CustomerEligibilityHttpLookup lookup = new CustomerEligibilityHttpLookup(http, () -> actor);

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    private MerchantEligibilityDTO allowed(String payee) {
        return new MerchantEligibilityDTO(true, "ALLOWED",
                new MerchantProfileDTO(payee, "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true),
                1, "grant", 1L);
    }

    private void respond(MerchantEligibilityDTO value) {
        when(http.authorize(anyString(), any())).thenReturn(SuccessResponse.of(value));
    }

    @Test
    void delegatesTokenPerRequestAndAcceptsOnlyFactsForTheExpectedPayee() {
        respond(allowed("merchant"));
        assertTrue(lookup.authorizeNewOrder("tenant", "merchant").allowed());
        verify(http).authorize(eq("Bearer " + TOKEN), argThat(command ->
                command.payeeMerchantId().equals("merchant") && command.action().name().equals("CREATE_ORDER")));
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "another-merchant"));
    }

    @Test
    void wrongTenantMissingAudienceAndActiveTransactionsPreventNetworkAccess() {
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("another-tenant", "merchant"));
        var missingAudience = new CustomerEligibilityHttpLookup(http, () ->
                new ProductActor("tenant", "MERCHANT_APP", "app", "merchant", null, Set.of()));
        assertThrows(CommonException.class, () -> missingAudience.authorizeNewOrder("tenant", "merchant"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
        verifyNoInteractions(http);
    }

    @Test
    void onlyWhitelistedNotFoundBecomesABusinessDenial() {
        when(http.authorize(anyString(), any())).thenReturn(new SuccessResponse<>(
                Result.buildFailure(CustomerResultCodeEnum.NOT_FOUND), null));
        assertEquals(CustomerResultCodeEnum.NOT_FOUND.getCode(),
                lookup.authorizeNewOrder("tenant", "merchant").reasonCode());
        when(http.authorize(anyString(), any())).thenReturn(new SuccessResponse<>(
                Result.buildFailure(CustomerResultCodeEnum.ACTION_FORBIDDEN), null));
        var error = assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
        assertSame(ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE, error.getResultCode());
        respond(new MerchantEligibilityDTO(false, CustomerResultCodeEnum.MERCHANT_INACTIVE.getCode(), null, 0, null, null));
        assertFalse(lookup.authorizeNewOrder("tenant", "merchant").allowed());
    }

    @Test
    void malformedRepliesAndTransportFailuresStayDependencyFailures() {
        respond(null);
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
        respond(new MerchantEligibilityDTO(true, "ALLOWED", allowed("merchant").merchant(), 0, "grant", 1L));
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
        when(http.authorize(anyString(), any())).thenThrow(new IllegalStateException("sensitive remote detail"));
        var error = assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
        assertSame(ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE, error.getResultCode());
        assertNull(error.getCause());
    }
}
