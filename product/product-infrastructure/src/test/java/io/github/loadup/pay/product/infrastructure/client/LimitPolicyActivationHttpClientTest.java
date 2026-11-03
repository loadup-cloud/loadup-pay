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
package io.github.loadup.pay.product.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.pay.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class LimitPolicyActivationHttpClientTest {
    final LimitCenterHttpService remote = mock(LimitCenterHttpService.class);
    final ServiceAccessTokens tokens = mock(ServiceAccessTokens.class);
    final LimitPolicyActivationHttpClient client = new LimitPolicyActivationHttpClient(
            remote, new PolicyPublicationTransportProperties("tenant", "product", "limitcenter"), tokens);

    @Test
    void eachAttemptUsesTheSharedCredentialSource() {
        when(tokens.token("tenant", "product", "limitcenter")).thenReturn("first", "second");
        var ack = new LimitPolicyActivationDTO("policy", 1, "a".repeat(64), OffsetDateTime.now());
        when(remote.activate(anyString(), isNull())).thenReturn(SuccessResponse.of(ack));
        assertEquals(ack, client.activate("tenant", null));
        assertEquals(ack, client.activate("tenant", null));
        verify(remote).activate("Bearer first", null);
        verify(remote).activate("Bearer second", null);
    }

    @Test
    void anotherTenantIsRejectedBeforeAuthorizationOrTransport() {
        assertThrows(CommonException.class, () -> client.activate("other", null));
        verifyNoInteractions(tokens, remote);
    }

    @Test
    void authorizationFailureDoesNotReachTheBusinessProviderOrLeakCredentials() {
        when(tokens.token(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("synthetic-secret"));
        var failure = assertThrows(CommonException.class, () -> client.activate("tenant", null));
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("synthetic-secret"));
        verifyNoInteractions(remote);
    }

    @Test
    void remoteBusinessFailureIsNotAnActivationAck() {
        when(tokens.token(anyString(), anyString(), anyString())).thenReturn("token");
        when(remote.activate(anyString(), isNull()))
                .thenReturn(
                        new SuccessResponse<>(Result.buildFailure(LimitCenterResultCodeEnum.POLICY_CONFLICT), null));
        assertThrows(CommonException.class, () -> client.activate("tenant", null));
    }
}
