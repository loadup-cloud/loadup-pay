/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.product.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.FailureResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LimitPolicyActivationHttpClientTest {
    final HttpTemplate http = mock(HttpTemplate.class);
    final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    @TempDir
    Path directory;

    LimitPolicyActivationHttpClient client(Path file) {
        return new LimitPolicyActivationHttpClient(
                http, json, new PolicyPublicationTransportProperties("tenant", file));
    }

    @Test
    void everyAttemptReadsTheRotatedServiceTokenWithoutPersistingIt() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "header.payload.signature-v1" + System.lineSeparator());
        var ack = new LimitPolicyActivationDTO(
                "policy", 1, "a".repeat(64), OffsetDateTime.parse("2026-10-21T10:00:00+08:00"));
        when(http.exchange(eq("limitcenter"), eq("activate"), any()))
                .thenReturn(ResponseEntity.ok(json.writeValueAsBytes(SuccessResponse.of(ack))));
        var client = client(file);
        client.activate("tenant", null);
        Files.writeString(file, "header.payload.signature-v2" + System.lineSeparator());
        client.activate("tenant", null);
        verify(http)
                .exchange(
                        eq("limitcenter"),
                        eq("activate"),
                        argThat(call -> call.headers().get("Authorization").endsWith("signature-v1")));
        verify(http)
                .exchange(
                        eq("limitcenter"),
                        eq("activate"),
                        argThat(call -> call.headers().get("Authorization").endsWith("signature-v2")));
    }

    @Test
    void anotherTenantCannotUseTheConfiguredPublisherCredential() {
        assertThrows(
                CommonException.class,
                () -> client(directory.resolve("missing")).activate("other", null));
        verifyNoInteractions(http);
    }

    @Test
    void missingOrOversizedCredentialsFailWithoutAnHttpRequest() throws Exception {
        var file = directory.resolve("token");
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        Files.writeString(file, "a".repeat(16385));
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        verifyNoInteractions(http);
    }

    @Test
    void anHttpSuccessWithABusinessFailureIsNotAnActivationAck() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "header.payload.signature");
        when(http.exchange(eq("limitcenter"), eq("activate"), any()))
                .thenReturn(ResponseEntity.ok(json.writeValueAsBytes(FailureResponse.of(
                        io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.POLICY_CONFLICT))));
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
    }
}
