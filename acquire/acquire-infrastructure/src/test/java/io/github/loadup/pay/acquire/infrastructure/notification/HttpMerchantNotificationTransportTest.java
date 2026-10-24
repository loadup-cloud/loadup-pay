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
package io.github.loadup.pay.acquire.infrastructure.notification;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpProperties;
import io.github.loadup.components.http.HttpRequestSigner;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.components.kms.KmsKeyRef;
import io.github.loadup.components.kms.KmsSignature;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.components.signature.request.RequestContent;
import io.github.loadup.components.signature.request.RequestSignature;
import io.github.loadup.components.signature.request.RequestSignatureProperties;
import io.github.loadup.components.signature.request.RequestSigner;
import io.github.loadup.components.signature.request.RequestSigningIdentity;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationPolicy;
import io.github.loadup.pay.acquire.infrastructure.config.MerchantNotificationProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpMerchantNotificationTransportTest {
    private static final String TENANT = "tenant-a";
    private static final String MERCHANT = "merchant-a";
    private static final String EVENT = "event-a";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final URI TARGET_URI = URI.create("https://hooks.example.test/v1/events");
    private static final String BODY = "{\"eventId\":\"event-a\",\"eventType\":\"PaymentSucceeded\","
            + "\"schemaVersion\":1,\"producer\":\"acquire\",\"aggregateType\":\"PaymentOrder\","
            + "\"aggregateId\":\"payment-a\",\"aggregateVersion\":7,"
            + "\"occurredAt\":\"" + BusinessTime.external(NOW).toString() + "\",\"data\":{\"orderId\":\"order-a\","
            + "\"paymentId\":\"payment-a\",\"payeeMerchantId\":\"merchant-a\","
            + "\"amountMinor\":1250,\"currency\":\"CNY\",\"orderPaidAmountMinor\":1250,"
            + "\"orderAmountMinor\":1250,\"orderState\":\"PAID\"}}";
    private static final byte[] BODY_BYTES = BODY.getBytes(StandardCharsets.UTF_8);
    private static final RequestSigningIdentity IDENTITY = new RequestSigningIdentity(
            "merchant-app", "merchant-notifications", new KmsKeyRef("notify-key", 3), KmsSignatureAlgorithm.RSA_SHA256_PSS);

    @Test
    void sendsFixedHttpOperationAndSignsExactRawTargetAndFrozenBody() {
        var fixture = fixture(validProperties(), validHttpProperties());
        var signature = mock(RequestSignature.class);
        when(signature.toHeaders()).thenReturn(Map.of("X-Test-Signature", "signed"));
        var signer = mock(RequestSigner.class);
        fixture.signers().set(signer);
        var content = new ArrayList<RequestContent>();
        doAnswer(call -> {
            String client = call.getArgument(0);
            String operation = call.getArgument(1);
            HttpCall request = call.getArgument(2);
            HttpRequestSigner callback = call.getArgument(3);
            assertEquals("merchant-hook", client);
            assertEquals("deliver", operation);
            assertEquals("application/json", request.headers().get("Content-Type"));
            assertEquals("application/json", request.headers().get("Accept"));
            assertTrue(request.query().isEmpty());
            assertArrayEquals(BODY_BYTES, (byte[]) request.body());
            var headers = callback.sign(HttpMethod.POST, TARGET_URI, BODY_BYTES.clone());
            assertEquals("signed", headers.get("X-Test-Signature"));
            return ok(ack(EVENT));
        }).when(fixture.httpTemplate()).exchange(eq("merchant-hook"), eq("deliver"), any(HttpCall.class), any(HttpRequestSigner.class));
        when(signer.sign(eq(IDENTITY), any(RequestContent.class))).thenAnswer(call -> {
            content.add(call.getArgument(1));
            return signature;
        });

        TenantUtil.runWithTenant(TENANT, () -> {
            assertTrue(fixture.transport().send(TENANT, fixture.delivery()));
            assertTrue(fixture.transport().send(TENANT, fixture.delivery()));
        });

        assertEquals(2, content.size());
        for (RequestContent signed : content) {
            assertEquals("POST", signed.method());
            assertEquals("/v1/events", signed.target());
            assertEquals("application/json", signed.contentType());
            assertArrayEquals(BODY_BYTES, signed.payload());
        }
    }

    @Test
    void realSignerCreatesFreshNoncesForRepeatedIdenticalRequestContent() {
        var fixture = fixture(validProperties(), validHttpProperties());
        var kms = mock(KmsTemplate.class);
        when(kms.sign(eq(IDENTITY.key()), eq(IDENTITY.algorithm()), any(byte[].class)))
                .thenAnswer(call -> new KmsSignature("notify-key", 3, KmsSignatureAlgorithm.RSA_SHA256_PSS, new byte[256]));
        fixture.signers().set(new RequestSigner(kms, java.time.Clock.systemUTC(), new RequestSignatureProperties(null, null, null)));
        var nonces = new ArrayList<String>();
        doAnswer(call -> {
            HttpRequestSigner callback = call.getArgument(3);
            nonces.add(callback.sign(HttpMethod.POST, TARGET_URI, BODY_BYTES.clone()).get("X-Nonce"));
            return ok(ack(EVENT));
        }).when(fixture.httpTemplate()).exchange(eq("merchant-hook"), eq("deliver"), any(HttpCall.class), any(HttpRequestSigner.class));

        TenantUtil.runWithTenant(TENANT, () -> {
            assertTrue(fixture.transport().send(TENANT, fixture.delivery()));
            assertTrue(fixture.transport().send(TENANT, fixture.delivery()));
        });

        assertEquals(2, nonces.size());
        assertNotEquals(nonces.get(0), nonces.get(1));
    }

    @Test
    void acceptsOnlyExactSemanticAcknowledgements() {
        var fixture = fixture(validProperties(), validHttpProperties());
        var signer = mock(RequestSigner.class);
        var signature = mock(RequestSignature.class);
        when(signature.toHeaders()).thenReturn(Map.of("X-Test-Signature", "signed"));
        when(signer.sign(eq(IDENTITY), any(RequestContent.class))).thenReturn(signature);
        fixture.signers().set(signer);
        var response = new AtomicReference<>(ok(ack(EVENT)));
        doAnswer(call -> {
            HttpRequestSigner callback = call.getArgument(3);
            callback.sign(HttpMethod.POST, TARGET_URI, BODY_BYTES.clone());
            return response.get();
        }).when(fixture.httpTemplate()).exchange(eq("merchant-hook"), eq("deliver"), any(HttpCall.class), any(HttpRequestSigner.class));

        TenantUtil.runWithTenant(TENANT, () -> {
            assertTrue(fixture.transport().send(TENANT, fixture.delivery()));
            List<byte[]> rejected = List.of(
                    new byte[0],
                    ack("foreign-event"),
                    bytes("{\"eventId\":\"event-a\",\"result\":\"SUCCESS\",\"extra\":true}"),
                    bytes("{\"eventId\":\"event-a\",\"eventId\":\"event-a\",\"result\":\"SUCCESS\"}"),
                    bytes("{\"eventId\":\"event-a\",\"result\":\"SUCCESS\"} {\"extra\":1}"),
                    bytes("{broken"),
                    new byte[] {(byte) 0xc3, 0x28},
                    bytes("{\"eventId\":\"event-a\",\"result\":1}"));
            for (byte[] body : rejected) {
                response.set(ok(body));
                assertFalse(fixture.transport().send(TENANT, fixture.delivery()));
            }
            response.set(ResponseEntity.status(204).build());
            assertFalse(fixture.transport().send(TENANT, fixture.delivery()));
            response.set(ResponseEntity.status(503).body(ack(EVENT)));
            assertFalse(fixture.transport().send(TENANT, fixture.delivery()));
        });
    }

    @Test
    void rejectsInvalidTenantCatalogAndHttpConfigurationBeforeHttp() {
        var valid = fixture(validProperties(), validHttpProperties());
        assertThrows(CommonException.class, () -> valid.transport().send(TENANT, valid.delivery()));
        verify(valid.httpTemplate(), never()).exchange(any(), any(), any(), any(HttpRequestSigner.class));

        TenantUtil.runWithTenant("foreign-tenant", () -> assertThrows(CommonException.class,
                () -> valid.transport().fingerprint(TENANT, valid.delivery())));

        var disabled = fixture(properties(Map.of()), validHttpProperties());
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant(TENANT,
                () -> disabled.transport().send(TENANT, disabled.delivery())));
        verify(disabled.httpTemplate(), never()).exchange(any(), any(), any(), any(HttpRequestSigner.class));

        var wrongMerchant = fixture(properties(Map.of(TENANT, Map.of("target-a",
                new MerchantNotificationProperties.Target("merchant-b", "merchant-hook", "deliver", TARGET_URI)))), validHttpProperties());
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant(TENANT,
                () -> wrongMerchant.transport().send(TENANT, wrongMerchant.delivery())));
        verify(wrongMerchant.httpTemplate(), never()).exchange(any(), any(), any(), any(HttpRequestSigner.class));

        for (HttpProperties bad : List.of(httpProperties(true, false), httpProperties(false, true))) {
            var rejected = fixture(validProperties(), bad);
            assertThrows(CommonException.class, () -> TenantUtil.runWithTenant(TENANT,
                    () -> rejected.transport().send(TENANT, rejected.delivery())));
            verify(rejected.httpTemplate(), never()).exchange(any(), any(), any(), any(HttpRequestSigner.class));
        }
    }

    @Test
    void rejectsMismatchedFingerprintAndTamperedFinalRequestBeforeSigning() {
        var valid = fixture(validProperties(), validHttpProperties());
        var changed = withRoute(valid.delivery(), "0".repeat(64));
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant(TENANT,
                () -> valid.transport().send(TENANT, changed)));
        verify(valid.httpTemplate(), never()).exchange(any(), any(), any(), any(HttpRequestSigner.class));

        var signer = mock(RequestSigner.class);
        valid.signers().set(signer);
        doAnswer(call -> {
            HttpRequestSigner callback = call.getArgument(3);
            return callback.sign(HttpMethod.POST, URI.create("https://other.example.test/v1/events"), BODY_BYTES.clone());
        }).when(valid.httpTemplate()).exchange(eq("merchant-hook"), eq("deliver"), any(HttpCall.class), any(HttpRequestSigner.class));
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant(TENANT,
                () -> valid.transport().send(TENANT, valid.delivery())));
        verify(signer, never()).sign(any(), any(RequestContent.class));

        doAnswer(call -> {
            HttpRequestSigner callback = call.getArgument(3);
            byte[] changedBody = BODY_BYTES.clone();
            changedBody[0] = '[';
            return callback.sign(HttpMethod.POST, TARGET_URI, changedBody);
        }).when(valid.httpTemplate()).exchange(eq("merchant-hook"), eq("deliver"), any(HttpCall.class), any(HttpRequestSigner.class));
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant(TENANT,
                () -> valid.transport().send(TENANT, valid.delivery())));
        verify(signer, never()).sign(any(), any(RequestContent.class));
    }

    @Test
    void rejectsFrozenBodySemanticMismatchesBeforeHttp() {
        var fixture = fixture(validProperties(), validHttpProperties());
        fixture.signers().set(mock(RequestSigner.class));
        List<String> invalidBodies = List.of(
                BODY.replace("\"currency\":\"CNY\"", "\"currency\":\"USD\""),
                BODY.replace("\"aggregateType\":\"PaymentOrder\"", "\"aggregateType\":\"AcquireOrder\""),
                BODY.replace("\"aggregateVersion\":7", "\"aggregateVersion\":8"),
                BODY.replace("\"amountMinor\":1250", "\"amountMinor\":0"));

        TenantUtil.runWithTenant(TENANT, () -> {
            for (String body : invalidBodies) {
                assertThrows(CommonException.class, () -> fixture.transport().send(TENANT,
                        withBody(fixture.delivery(), body)));
            }
        });
        verify(fixture.httpTemplate(), never()).exchange(any(), any(), any(), any(HttpRequestSigner.class));
    }

    @Test
    void rejectsQueryBearingTargetUri() {
        assertThrows(IllegalArgumentException.class, () -> new MerchantNotificationProperties.Target(
                MERCHANT, "merchant-hook", "deliver", URI.create("https://hooks.example.test/v1/events?tenant=tenant-a")));
    }

    @Test
    void refundNotificationsUseTheSameFixedByteTransportAndSemanticAck() {
        var fixture = fixture(validProperties(), validHttpProperties());
        fixture.signers().set(mock(RequestSigner.class));
        var sent = new ArrayList<byte[]>();
        doAnswer(call -> {
            HttpCall request = call.getArgument(2);
            sent.add((byte[]) request.body());
            return ok(ack(EVENT));
        }).when(fixture.httpTemplate()).exchange(any(), any(), any(), any(HttpRequestSigner.class));
        var successBody = refundBody("RefundSucceeded", "RefundOrder", "refund-a", "SUCCEEDED", 1250, 0);
        var requestBody = refundBody("RefundRequestCompleted", "RefundRequest", "request-a", "PARTIAL_SUCCEEDED", 1000, 250);
        TenantUtil.runWithTenant(TENANT, () -> {
            assertTrue(fixture.transport().send(TENANT, refundDelivery(fixture.delivery(), "RefundSucceeded", "refund-a", successBody)));
            assertTrue(fixture.transport().send(TENANT, refundDelivery(fixture.delivery(), "RefundRequestCompleted", "request-a", requestBody)));
        });
        assertEquals(2, sent.size());
        assertArrayEquals(bytes(successBody), sent.get(0));
        assertArrayEquals(bytes(requestBody), sent.get(1));
    }

    @Test
    void corruptRefundTypeAmountsScopeAndTerminalFactsAreRejectedBeforeHttp() {
        var fixture = fixture(validProperties(), validHttpProperties());
        fixture.signers().set(mock(RequestSigner.class));
        String valid = refundBody("RefundSucceeded", "RefundOrder", "refund-a", "SUCCEEDED", 1250, 0);
        TenantUtil.runWithTenant(TENANT, () -> {
            for (var corrupt : List.of(valid.replace("\"producer\":\"payment\"", "\"producer\":\"acquire\""),
                    valid.replace("\"succeededMinor\":1250", "\"succeededMinor\":1249"),
                    valid.replace("\"payeeMerchantId\":\"merchant-a\"", "\"payeeMerchantId\":\"other\""),
                    valid.replace("\"aggregateType\":\"RefundOrder\"", "\"aggregateType\":\"RefundRequest\""),
                    valid.replace("\"state\":\"SUCCEEDED\"", "\"state\":\"UNKNOWN\""))) {
                assertThrows(CommonException.class, () -> fixture.transport().send(TENANT,
                        refundDelivery(fixture.delivery(), "RefundSucceeded", "refund-a", corrupt)));
            }
        });
        verify(fixture.httpTemplate(), never()).exchange(any(), any(), any(), any(HttpRequestSigner.class));
    }

    private static String refundBody(String type, String aggregateType, String aggregate, String state, long succeeded, long failed) {
        boolean child = "RefundOrder".equals(aggregateType);
        return JsonUtil.toJson(new io.github.loadup.pay.acquire.app.model.MerchantNotificationBody<>(EVENT, type, 1, "payment",
                aggregateType, aggregate, 7, BusinessTime.external(NOW), new io.github.loadup.pay.acquire.app.model.MerchantRefundNotificationData(
                        "order-a", "request-a", "R001", child ? "refund-a" : null, child ? "payment-a" : null, MERCHANT,
                        1250, "CNY", state, succeeded, failed, child && "SUCCEEDED".equals(state) ? BusinessTime.external(NOW.minusSeconds(1)) : null)));
    }

    private static MerchantNotificationDelivery refundDelivery(MerchantNotificationDelivery original, String type, String aggregate, String body) {
        return new MerchantNotificationDelivery(original.id(), original.eventId(), original.orderId(), original.payeeMerchantId(), type,
                aggregate, original.sourceVersion(), body, digest(bytes(body)), original.policy(), original.occurredAt(), original.expiresAt(),
                original.nextAttemptAt(), original.attemptCount(), original.state(), original.routeFingerprint(), original.acknowledgedAt(), original.rowVersion());
    }

    private static Fixture fixture(MerchantNotificationProperties properties, HttpProperties httpProperties) {
        var httpTemplate = mock(HttpTemplate.class);
        var httpProvider = provider(httpTemplate);
        var httpPropertiesProvider = provider(httpProperties);
        var signerHolder = new AtomicReference<RequestSigner>();
        @SuppressWarnings("unchecked")
        ObjectProvider<RequestSigner> signerProvider = mock(ObjectProvider.class);
        when(signerProvider.getIfAvailable()).thenAnswer(call -> signerHolder.get());
        var transport = new HttpMerchantNotificationTransport(properties, httpProvider, httpPropertiesProvider,
                signerProvider, JsonUtil.customize(JsonMapper.builder()).build());
        var policy = new MerchantNotificationPolicy("target-a", "identity-a", 10000, 5,
                java.math.BigDecimal.ONE, 60, 5, 3600);
        var initial = MerchantNotificationDelivery.init("delivery-a", EVENT, "order-a", MERCHANT,
                "PaymentSucceeded", "payment-a", 7, BODY, digest(BODY_BYTES), policy, NOW);
        String route = TenantUtil.callWithTenant(TENANT, () -> {
            try { return transport.fingerprint(TENANT, initial); }
            catch (CommonException invalidCatalog) { return "0".repeat(64); }
        });
        var delivery = initial.begin(NOW, route);
        return new Fixture(transport, httpTemplate, signerHolder, delivery);
    }

    private static MerchantNotificationDelivery withRoute(MerchantNotificationDelivery delivery, String route) {
        return new MerchantNotificationDelivery(delivery.id(), delivery.eventId(), delivery.orderId(),
                delivery.payeeMerchantId(), delivery.eventType(), delivery.aggregateId(), delivery.sourceVersion(),
                delivery.body(), delivery.bodyDigest(), delivery.policy(), delivery.occurredAt(), delivery.expiresAt(),
                delivery.nextAttemptAt(), delivery.attemptCount(), delivery.state(), route,
                delivery.acknowledgedAt(), delivery.rowVersion());
    }

    private static MerchantNotificationDelivery withBody(MerchantNotificationDelivery delivery, String body) {
        return new MerchantNotificationDelivery(delivery.id(), delivery.eventId(), delivery.orderId(),
                delivery.payeeMerchantId(), delivery.eventType(), delivery.aggregateId(), delivery.sourceVersion(),
                body, digest(bytes(body)), delivery.policy(), delivery.occurredAt(), delivery.expiresAt(),
                delivery.nextAttemptAt(), delivery.attemptCount(), delivery.state(), delivery.routeFingerprint(),
                delivery.acknowledgedAt(), delivery.rowVersion());
    }

    private static MerchantNotificationProperties validProperties() {
        return properties(Map.of(TENANT, Map.of("target-a", new MerchantNotificationProperties.Target(
                MERCHANT, "merchant-hook", "deliver", TARGET_URI))));
    }

    private static MerchantNotificationProperties properties(Map<String, Map<String, MerchantNotificationProperties.Target>> targets) {
        return new MerchantNotificationProperties(targets, Map.of(TENANT, Map.of("identity-a",
                new MerchantNotificationProperties.Signing(MERCHANT, IDENTITY))));
    }

    private static HttpProperties validHttpProperties() {
        return httpProperties(false, false);
    }

    private static HttpProperties httpProperties(boolean allowPrivate, boolean timeoutTooLarge) {
        Duration ordinary = Duration.ofMillis(timeoutTooLarge ? 4000 : 100);
        var client = new HttpProperties.Client(URI.create("https://hooks.example.test"), ordinary,
                ordinary, ordinary, 10, 10, 65536, 4096L, null, null, allowPrivate, null,
                Map.of(), Map.of("deliver", new HttpProperties.Operation(HttpMethod.POST, "/v1/events", Map.of())));
        return new HttpProperties(Map.of("merchant-hook", client));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static byte[] ack(String eventId) {
        return bytes("{\"eventId\":\"" + eventId + "\",\"result\":\"SUCCESS\"}");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String digest(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static ResponseEntity<byte[]> ok(byte[] body) {
        return ResponseEntity.ok(body);
    }

    private record Fixture(HttpMerchantNotificationTransport transport, HttpTemplate httpTemplate,
            AtomicReference<RequestSigner> signers, MerchantNotificationDelivery delivery) {}
}
