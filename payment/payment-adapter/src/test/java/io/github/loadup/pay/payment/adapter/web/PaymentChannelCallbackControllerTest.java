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
import java.util.Collections;
import java.util.List;

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
    private final io.github.loadup.pay.payment.client.facade.RefundChannelCallbackFacade refundCallbacks =
            mock(io.github.loadup.pay.payment.client.facade.RefundChannelCallbackFacade.class);
    private final PaymentChannelCallbackController controller = new PaymentChannelCallbackController(callbacks, refundCallbacks);

    @Test
    void refundEndpointDelegatesRawJsonAndSignatureHeadersOnlyToRefundFacade() throws IOException {
        byte[] body = "{\"refund\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request = request(body, null);
        when(request.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of("Wechatpay-Signature")));
        when(request.getHeaders("Wechatpay-Signature")).thenReturn(Collections.enumeration(List.of("refund-signature")));
        when(refundCallbacks.receive(eq(ENDPOINT_REF), any(ChannelNotificationInput.class)))
                .thenReturn(new ChannelNotificationAcknowledgement(200, "application/json", "{\"code\":\"SUCCESS\"}"));

        ResponseEntity<String> response = controller.receiveRefund(ENDPOINT_REF, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"code\":\"SUCCESS\"}", response.getBody());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        var captured = org.mockito.ArgumentCaptor.forClass(ChannelNotificationInput.class);
        verify(refundCallbacks).receive(eq(ENDPOINT_REF), captured.capture());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, captured.getValue().contentType());
        assertArrayEquals(body, captured.getValue().body());
        assertEquals(java.util.Map.of("wechatpay-signature", List.of("refund-signature")), captured.getValue().headers());
        verify(callbacks, never()).receive(anyString(), any());
    }

    @Test
    void refundEndpointPropagatesNativeNegativeAcknowledgement() throws IOException {
        var request = request("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        when(request.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(refundCallbacks.receive(eq(ENDPOINT_REF), any(ChannelNotificationInput.class)))
                .thenReturn(new ChannelNotificationAcknowledgement(500, "text/plain", "retry"));

        ResponseEntity<String> response = controller.receiveRefund(ENDPOINT_REF, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("retry", response.getBody());
        verify(refundCallbacks).receive(eq(ENDPOINT_REF), any(ChannelNotificationInput.class));
        verify(callbacks, never()).receive(anyString(), any());
    }

    @Test
    void validRequestReturnsOnlyTheFixedNativeAcknowledgementAndPassesRawBytes() throws IOException {
        byte[] body = "notify=raw%2Bbody&sign=signature".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request = request(body, null);
        var acknowledgement = new ChannelNotificationAcknowledgement(200, "text/plain", "provider-native-ack");
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
        assertEquals(java.util.Map.of(), captured.getValue().headers());
        verify(request, never()).getHeader(anyString());
        verify(request, never()).getParameter(anyString());
    }

    @Test
    void propagatesNativeNegativeAcknowledgementStatus() throws IOException {
        var request = request("notify=raw".getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        when(callbacks.receive(eq(ENDPOINT_REF), any(ChannelNotificationInput.class)))
                .thenReturn(new ChannelNotificationAcknowledgement(503, "text/plain", "retry"));

        ResponseEntity<String> response = controller.receive(ENDPOINT_REF, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("retry", response.getBody());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
    }

    @Test
    void capturesRawBytesAndVendorHeadersWithoutAChannelSpecificAllowlist() throws IOException {
        byte[] body = "{\"event\":\"paid\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request = request(body, null);
        when(request.getContentType()).thenReturn(MediaType.APPLICATION_JSON_VALUE);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of(
                "Wechatpay-Serial", "Wechatpay-Timestamp", "Wechatpay-Nonce", "Wechatpay-Signature",
                "Wechatpay-Signature-Type", "X-Tenant", "X-Another-Provider-Signature")));
        when(request.getHeaders("Wechatpay-Serial")).thenReturn(Collections.enumeration(List.of("serial-1", "serial-2")));
        when(request.getHeaders("Wechatpay-Timestamp")).thenReturn(Collections.enumeration(List.of("timestamp")));
        when(request.getHeaders("Wechatpay-Nonce")).thenReturn(Collections.enumeration(List.of("nonce")));
        when(request.getHeaders("Wechatpay-Signature")).thenReturn(Collections.enumeration(List.of("sig-1", "sig-2")));
        when(request.getHeaders("Wechatpay-Signature-Type")).thenReturn(Collections.enumeration(List.of("WECHATPAY2-SHA256-RSA2048")));
        when(request.getHeaders("X-Tenant")).thenReturn(Collections.enumeration(List.of("attacker-tenant")));
        when(request.getHeaders("X-Another-Provider-Signature")).thenReturn(Collections.enumeration(List.of("opaque-signature")));
        when(callbacks.receive(eq(ENDPOINT_REF), any(ChannelNotificationInput.class)))
                .thenReturn(new ChannelNotificationAcknowledgement(200, "text/plain", "ok"));

        controller.receive(ENDPOINT_REF, request);

        var captured = org.mockito.ArgumentCaptor.forClass(ChannelNotificationInput.class);
        verify(callbacks).receive(eq(ENDPOINT_REF), captured.capture());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, captured.getValue().contentType());
        assertArrayEquals(body, captured.getValue().body());
        assertEquals(java.util.Map.of(
                "wechatpay-serial", List.of("serial-1", "serial-2"),
                "wechatpay-timestamp", List.of("timestamp"),
                "wechatpay-nonce", List.of("nonce"),
                "wechatpay-signature", List.of("sig-1", "sig-2"),
                "wechatpay-signature-type", List.of("WECHATPAY2-SHA256-RSA2048"),
                "x-tenant", List.of("attacker-tenant"),
                "x-another-provider-signature", List.of("opaque-signature")), captured.getValue().headers());
        verify(request, never()).getParameter(anyString());
    }

    @Test
    void tooManyHeaderNamesAreRejectedBeforeCallingTheFacade() throws IOException {
        var request = request("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        var names = java.util.stream.IntStream.rangeClosed(0, ChannelNotificationInput.MAX_HEADER_NAMES)
                .mapToObj(index -> "X-Header-" + index).toList();
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(names));
        for (var name : names) when(request.getHeaders(name)).thenReturn(Collections.enumeration(List.of("value")));

        assertEquals(HttpStatus.BAD_REQUEST, controller.receive(ENDPOINT_REF, request).getStatusCode());
        verify(callbacks, never()).receive(anyString(), any());
    }

    @Test
    void tooManyValuesAreRejectedInsteadOfDroppingDuplicateSignatureHeaders() throws IOException {
        var request = request("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of("X-Provider-Signature")));
        when(request.getHeaders("X-Provider-Signature")).thenReturn(Collections.enumeration(
                Collections.nCopies(ChannelNotificationInput.MAX_HEADER_VALUES + 1, "signature")));

        assertEquals(HttpStatus.BAD_REQUEST, controller.receive(ENDPOINT_REF, request).getStatusCode());
        verify(callbacks, never()).receive(anyString(), any());
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
