package io.github.loadup.pay.product.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.pay.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.commons.result.Result;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LimitPolicyActivationHttpClientTest {
    final LimitCenterHttpService limitCenterHttp = mock(LimitCenterHttpService.class);
    final List<String> attachments = new ArrayList<>();

    @TempDir
    Path directory;

    LimitPolicyActivationHttpClient client(Path file) {
        var client = new LimitPolicyActivationHttpClient(
                limitCenterHttp, new PolicyPublicationTransportProperties("tenant", file));
        return client;
    }

    @Test
    void everyAttemptReadsTheRotatedServiceTokenWithoutPersistingIt() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "header.payload.signature-v1" + System.lineSeparator());
        var ack = new LimitPolicyActivationDTO(
                "policy", 1, "a".repeat(64), OffsetDateTime.parse("2026-10-21T10:00:00+08:00"));
        doAnswer(invocation -> {
                    attachments.add(invocation.getArgument(0, String.class));
                    return SuccessResponse.of(ack);
                })
                .when(limitCenterHttp)
                .activate(anyString(), isNull());
        var client = client(file);
        client.activate("tenant", null);
        Files.writeString(file, "header.payload.signature-v2" + System.lineSeparator());
        client.activate("tenant", null);
        verify(limitCenterHttp, times(2)).activate(anyString(), isNull());
        assertEquals(List.of("Bearer header.payload.signature-v1", "Bearer header.payload.signature-v2"), attachments);
    }

    @Test
    void anotherTenantCannotUseTheConfiguredPublisherCredential() {
        assertThrows(
                CommonException.class,
                () -> client(directory.resolve("missing")).activate("other", null));
        verifyNoInteractions(limitCenterHttp);
    }

    @Test
    void missingOrOversizedCredentialsFailWithoutAnHttpCall() throws Exception {
        var file = directory.resolve("token");
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        Files.writeString(file, "a".repeat(16385));
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        verifyNoInteractions(limitCenterHttp);
    }

    @Test
    void aRemoteBusinessFailureIsNotAnActivationAck() throws Exception {
        var file = directory.resolve("token");
        Files.writeString(file, "header.payload.signature");
        doAnswer(invocation -> new SuccessResponse<>(Result.buildFailure(LimitCenterResultCodeEnum.POLICY_CONFLICT), null))
                .when(limitCenterHttp)
                .activate(anyString(), isNull());
        assertThrows(CommonException.class, () -> client(file).activate("tenant", null));
        verify(limitCenterHttp).activate(anyString(), isNull());
    }
}
