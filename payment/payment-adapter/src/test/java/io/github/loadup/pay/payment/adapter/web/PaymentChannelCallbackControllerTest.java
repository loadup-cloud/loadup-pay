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
package io.github.loadup.pay.payment.adapter.web;

import io.github.loadup.pay.channel.api.model.ChannelNotificationAcknowledgement;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.payment.client.facade.PaymentChannelCallbackFacade;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentChannelCallbackControllerTest {
    private static final String ENDPOINT_REF = "trusted-endpoint";

    private final PaymentChannelCallbackFacade callbacks = mock(PaymentChannelCallbackFacade.class);
    private final PaymentChannelCallbackController controller = new PaymentChannelCallbackController(callbacks);

    @Test
    void validRequestReturnsOnlyTheFixedNativeAcknowledgementAndPassesRawBytes() throws IOException {
        byte[] body = "notify=raw%2Bbody&sign=signature".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request = request(body, null);
        when(request.getHeader("X-Tenant")).thenReturn("attacker-tenant");
        when(request.getParameter("tenant")).thenReturn("attacker-tenant");
        when(request.getParameter("channel")).thenReturn("WECHAT");
        var acknowledgement = new ChannelNotificationAcknowledgement("text/plain", "provider-native-ack");
        when(callbacks.receive(eq(ENDPOINT_REF), any(ChannelNotificationInput.class))).thenReturn(acknowledgement);

        ResponseEntity<String> response = controller.receive(ENDPOINT_REF, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("provider-native-ack", response.getBody());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        var captured = org.mockito.ArgumentCaptor.forClass(ChannelNotificationInput.class);
        verify(callbacks).receive(eq(ENDPOINT_REF), captured.capture());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED_VALUE, captured.getValue().contentType());
        assertArrayEquals(body, captured.getValue().body());
        verify(request, never()).getHeader(anyString());
        verify(request, never()).getParameter(anyString());
    }

    @Test
    void emptyBodyIsRejectedBeforeCallingTheFacade() throws IOException {
        var response = controller.receive(ENDPOINT_REF, request(new byte[0], null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
        verify(callbacks, never()).receive(anyString(), any());
    }

    @Test
    void oversizedBodyIsRejectedAtTheBoundPlusOneBeforeCallingTheFacade() throws IOException {
        byte[] body = new byte[ChannelNotificationInput.MAX_BODY_BYTES + 1];
        Arrays.fill(body, (byte) 'x');

        var response = controller.receive(ENDPOINT_REF, request(body, null));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertNull(response.getBody());
        verify(callbacks, never()).receive(anyString(), any());
    }

    @Test
    void queryParametersCannotSupplyCallbackTenantOrOtherTrustedFields() throws IOException {
        var response = controller.receive(ENDPOINT_REF,
                request("notify=raw".getBytes(java.nio.charset.StandardCharsets.UTF_8), "tenant=attacker&channel=WECHAT"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(callbacks, never()).receive(anyString(), any());
    }

    private HttpServletRequest request(byte[] body, String queryString) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getInputStream()).thenReturn(new BodyInputStream(body));
        when(request.getContentType()).thenReturn(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        when(request.getQueryString()).thenReturn(queryString);
        return request;
    }

    private static final class BodyInputStream extends ServletInputStream {
        private final ByteArrayInputStream input;

        private BodyInputStream(byte[] body) { input = new ByteArrayInputStream(body); }

        @Override
        public int read() { return input.read(); }

        @Override
        public boolean isFinished() { return input.available() == 0; }

        @Override
        public boolean isReady() { return true; }

        @Override
        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
    }
}
