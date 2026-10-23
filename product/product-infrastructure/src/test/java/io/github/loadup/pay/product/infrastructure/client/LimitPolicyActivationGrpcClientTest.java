/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     rpc://www.apache.org/licenses/LICENSE-2.0
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
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.grpc.Metadata;
import io.grpc.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_ACTIVATE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LimitPolicyActivationGrpcClientTest {
    final GrpcTransport rpc = mock(GrpcTransport.class);
    final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    @TempDir
    Path directory;

    LimitPolicyActivationGrpcClient client(Path file) {
        return new LimitPolicyActivationGrpcClient(
                rpc, new PolicyPublicationTransportProperties("tenant", file));
    }

    @Test
    void everyAttemptReadsTheRotatedServiceTokenWithoutPersistingIt() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "header.payload.signature-v1" + System.lineSeparator());
        var ack = new LimitPolicyActivationDTO(
                "policy", 1, "a".repeat(64), OffsetDateTime.parse("2026-10-21T10:00:00+08:00"));
        when(rpc.call(eq(LIMITCENTER_ACTIVATE), any(), any()))
                .thenReturn(ack);
        var client = client(file);
        client.activate("tenant", null);
        Files.writeString(file, "header.payload.signature-v2" + System.lineSeparator());
        client.activate("tenant", null);
        verify(rpc)
                .call(eq(LIMITCENTER_ACTIVATE), any(), argThat(token -> token.endsWith("signature-v1")));
        verify(rpc)
                .call(eq(LIMITCENTER_ACTIVATE), any(), argThat(token -> token.endsWith("signature-v2")));
    }

    @Test
    void anotherTenantCannotUseTheConfiguredPublisherCredential() {
        assertThrows(
                CommonException.class,
                () -> client(directory.resolve("missing")).activate("other", null));
        verifyNoInteractions(rpc);
    }

    @Test
    void missingOrOversizedCredentialsFailWithoutAnRpcCall() throws Exception {
        var file = directory.resolve("token");
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        Files.writeString(file, "a".repeat(16385));
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        verifyNoInteractions(rpc);
    }

    @Test
    void aRemoteBusinessFailureIsNotAnActivationAck() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "header.payload.signature");
        when(rpc.call(eq(LIMITCENTER_ACTIVATE), any(), any()))
                .thenThrow(remoteFailure((
                        io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.POLICY_CONFLICT).getCode()));
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
    }
    private static io.grpc.StatusRuntimeException remoteFailure(String code) {
        var trailers = new Metadata();
        trailers.put(RpcServerSupport.RESULT_CODE, code);
        return Status.FAILED_PRECONDITION.asRuntimeException(trailers);
    }
}
