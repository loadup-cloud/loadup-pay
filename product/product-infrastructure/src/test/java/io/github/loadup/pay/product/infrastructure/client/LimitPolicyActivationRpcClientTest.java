/*
 * #%L
 * LoadUp Pay Product Infrastructure
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
package io.github.loadup.pay.product.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcMapper;
import io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcMapperImpl;
import io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpc;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class LimitPolicyActivationRpcClientTest {
    final LimitCenterRpc limitCenterRpc = mock(LimitCenterRpc.class);
    final LimitCenterRpcMapper limitCenterRpcMapper = new LimitCenterRpcMapperImpl();
    final List<String> attachments = new ArrayList<>();

    @TempDir
    Path directory;

    private static RpcException remoteFailure(String code) {
        return new RpcException(RpcException.BIZ_EXCEPTION, "loadup-result-code=" + code);
    }

    LimitPolicyActivationRpcClient client(Path file) {
        var client = new LimitPolicyActivationRpcClient(
                limitCenterRpcMapper, new PolicyPublicationTransportProperties("tenant", file));
        ReflectionTestUtils.setField(client, "limitCenterRpc", limitCenterRpc);
        return client;
    }

    @Test
    void everyAttemptReadsTheRotatedServiceTokenWithoutPersistingIt() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "header.payload.signature-v1" + System.lineSeparator());
        var ack = new LimitPolicyActivationDTO(
                "policy", 1, "a".repeat(64), OffsetDateTime.parse("2026-10-21T10:00:00+08:00"));
        doAnswer(invocation -> {
                    attachments.add(RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
                    return limitCenterRpcMapper.toWireLimitPolicyActivationDTO(ack);
                })
                .when(limitCenterRpc)
                .activate(isNull());
        var client = client(file);
        client.activate("tenant", null);
        Files.writeString(file, "header.payload.signature-v2" + System.lineSeparator());
        client.activate("tenant", null);
        verify(limitCenterRpc, times(2)).activate(isNull());
        assertEquals(List.of("Bearer header.payload.signature-v1", "Bearer header.payload.signature-v2"), attachments);
    }

    @Test
    void anotherTenantCannotUseTheConfiguredPublisherCredential() {
        assertThrows(
                CommonException.class,
                () -> client(directory.resolve("missing")).activate("other", null));
        verifyNoInteractions(limitCenterRpc);
    }

    @Test
    void missingOrOversizedCredentialsFailWithoutAnRpcCall() throws Exception {
        var file = directory.resolve("token");
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        Files.writeString(file, "a".repeat(16385));
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        verifyNoInteractions(limitCenterRpc);
    }

    @Test
    void aRemoteBusinessFailureIsNotAnActivationAck() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "header.payload.signature");
        doThrow(remoteFailure(LimitCenterResultCodeEnum.POLICY_CONFLICT.getCode()))
                .when(limitCenterRpc)
                .activate(isNull());
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        verify(limitCenterRpc).activate(isNull());
    }
}
