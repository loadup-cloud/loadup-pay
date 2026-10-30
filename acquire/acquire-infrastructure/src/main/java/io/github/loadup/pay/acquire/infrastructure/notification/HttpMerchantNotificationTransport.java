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
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpProperties;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.components.signature.request.RequestContent;
import io.github.loadup.components.signature.request.RequestSigner;
import io.github.loadup.pay.acquire.app.model.MerchantNotificationBody;
import io.github.loadup.pay.acquire.app.model.MerchantNotificationData;
import io.github.loadup.pay.acquire.app.model.MerchantRefundNotificationData;
import io.github.loadup.pay.acquire.app.port.MerchantNotificationTransport;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.infrastructure.config.MerchantNotificationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Signed fixed-byte HTTPS delivery with strict semantic ACK; never uses gotone's HTTP-2xx-only success rule.
 */
@Component
public class HttpMerchantNotificationTransport implements MerchantNotificationTransport {
    private static final String CONTENT_TYPE = "application/json";
    private final MerchantNotificationProperties properties;
    private final ObjectProvider<HttpTemplate> http;
    private final ObjectProvider<HttpProperties> httpProperties;
    private final ObjectProvider<RequestSigner> signers;
    private final JsonMapper json;

    public HttpMerchantNotificationTransport(MerchantNotificationProperties properties, ObjectProvider<HttpTemplate> http,
                                             ObjectProvider<HttpProperties> httpProperties, ObjectProvider<RequestSigner> signers, JsonMapper json) {
        this.properties = properties;
        this.http = http;
        this.httpProperties = httpProperties;
        this.signers = signers;
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }

    @Override
    public String fingerprint(String tenant, MerchantNotificationDelivery delivery) {
        var target = target(tenant, delivery);
        var identity = signing(tenant, delivery).identity();
        return DigestUtils.sha256(JsonUtil.toJson(List.of("merchant-notification-route-v1", tenant, delivery.payeeMerchantId(),
                delivery.policy().targetRef(), delivery.policy().signingIdentityRef(), target.client(), target.operation(),
                target.uri().toASCIIString(), identity.appId(), identity.audience(), identity.key().name(), identity.key().version(),
                identity.algorithm().name(), delivery.policy().timeoutMillis())));
    }

    @Override
    public boolean send(String tenant, MerchantNotificationDelivery delivery) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (delivery.state() != io.github.loadup.pay.acquire.domain.model.MerchantNotificationState.PENDING || delivery.attemptCount() <= 0) {
            throw new CommonException(INVALID_STATE);
        }
        long started = System.nanoTime();
        if (delivery.routeFingerprint() == null || !delivery.routeFingerprint().equals(fingerprint(tenant, delivery))) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var target = target(tenant, delivery);
        var identity = signing(tenant, delivery).identity();
        configuration(target, delivery.policy().timeoutMillis());
        var template = http.getIfAvailable();
        var signer = signers.getIfAvailable();
        if (template == null || signer == null) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
        byte[] body = delivery.body().getBytes(StandardCharsets.UTF_8);
        if (body.length > 65536 || !checksum(body).equals(delivery.bodyDigest())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        validateBody(delivery, body);
        var response = template.exchange(target.client(), target.operation(), new HttpCall(Map.of(), Map.of(),
                Map.of("Content-Type", CONTENT_TYPE, "Accept", CONTENT_TYPE), body), (method, uri, sent) -> {
            if (method != HttpMethod.POST || !target.uri().toASCIIString().equals(uri.toASCIIString())
                    || !checksum(sent).equals(delivery.bodyDigest())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            String rawTarget = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            return signer.sign(identity, new RequestContent(method.name(), rawTarget, CONTENT_TYPE, sent)).toHeaders();
        });
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().length > 4096 || System.nanoTime() - started >= (long) delivery.policy().timeoutMillis() * 1_000_000L) {
            return false;
        }
        try {
            String ack = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.getBody())).toString();
            var data = json.readValue(ack, Map.class);
            return data != null && data.size() == 2 && delivery.eventId().equals(data.get("eventId"))
                    && "SUCCESS".equals(data.get("result"));
        } catch (java.nio.charset.CharacterCodingException | RuntimeException invalid) {
            return false;
        }
    }

    private void validateBody(MerchantNotificationDelivery delivery, byte[] body) {
        try {
            if (java.util.Set.of("RefundSucceeded", "RefundFailed", "RefundRequestCompleted").contains(delivery.eventType())) {
                var frozen = json.readValue(body, new TypeReference<MerchantNotificationBody<MerchantRefundNotificationData>>() {
                });
                metadata(delivery, frozen, "payment");
                refundData(delivery, frozen);
            } else if (java.util.Set.of("PaymentSucceeded", "OrderFullyPaid", "OrderClosed").contains(delivery.eventType())) {
                var frozen = json.readValue(body, new TypeReference<MerchantNotificationBody<MerchantNotificationData>>() {
                });
                metadata(delivery, frozen, "acquire");
                orderData(delivery, frozen);
            } else {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void metadata(MerchantNotificationDelivery delivery, MerchantNotificationBody<?> frozen, String producer) {
        if (frozen == null || frozen.schemaVersion() != 1 || !producer.equals(frozen.producer())
                || !delivery.eventId().equals(frozen.eventId()) || !delivery.eventType().equals(frozen.eventType())
                || !delivery.aggregateId().equals(frozen.aggregateId()) || delivery.sourceVersion() != frozen.aggregateVersion()
                || frozen.data() == null || frozen.occurredAt() == null || !frozen.occurredAt().toInstant().equals(
                io.github.loadup.commons.time.BusinessTime.external(delivery.occurredAt()).toInstant())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void orderData(MerchantNotificationDelivery delivery, MerchantNotificationBody<MerchantNotificationData> frozen) {
        var data = frozen.data();
        boolean paymentEvent = "PaymentSucceeded".equals(frozen.eventType());
        if (!delivery.orderId().equals(data.orderId()) || !delivery.payeeMerchantId().equals(data.payeeMerchantId())
                || !"CNY".equals(data.currency()) || data.amountMinor() <= 0 || data.orderAmountMinor() <= 0
                || data.orderPaidAmountMinor() < 0 || data.orderPaidAmountMinor() > data.orderAmountMinor()
                || !(paymentEvent ? "PaymentOrder".equals(frozen.aggregateType()) && frozen.aggregateId().equals(data.paymentId())
                && data.amountMinor() <= data.orderPaidAmountMinor()
                : "AcquireOrder".equals(frozen.aggregateType()) && frozen.aggregateId().equals(data.orderId())
                && data.paymentId() == null && data.amountMinor() == data.orderAmountMinor())
                || "OrderFullyPaid".equals(frozen.eventType()) && (!"PAID".equals(data.orderState())
                || data.orderPaidAmountMinor() != data.orderAmountMinor())
                || "OrderClosed".equals(frozen.eventType()) && !"CLOSED".equals(data.orderState())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void refundData(MerchantNotificationDelivery delivery, MerchantNotificationBody<MerchantRefundNotificationData> frozen) {
        var data = frozen.data();
        DomainChecks.id(data.orderId());
        DomainChecks.id(data.payeeMerchantId());
        DomainChecks.id(data.refundRequestId());
        DomainChecks.text(data.merchantRefundNo(), 64);
        if (!delivery.orderId().equals(data.orderId()) || !delivery.payeeMerchantId().equals(data.payeeMerchantId())
                || !"CNY".equals(data.currency()) || data.amountMinor() <= 0 || data.succeededMinor() < 0
                || data.succeededMinor() > data.amountMinor() || data.failedMinor() != data.amountMinor() - data.succeededMinor()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if ("RefundRequestCompleted".equals(frozen.eventType())) {
            if (!"RefundRequest".equals(frozen.aggregateType()) || !frozen.aggregateId().equals(data.refundRequestId())
                    || data.refundId() != null || data.paymentId() != null || data.succeededAt() != null
                    || data.state() == null || !java.util.Set.of("SUCCEEDED", "FAILED", "PARTIAL_SUCCEEDED").contains(data.state())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        } else {
            DomainChecks.id(data.refundId());
            DomainChecks.id(data.paymentId());
            boolean success = "RefundSucceeded".equals(frozen.eventType());
            if (!"RefundOrder".equals(frozen.aggregateType()) || !frozen.aggregateId().equals(data.refundId())
                    || !(success ? "SUCCEEDED" : "FAILED").equals(data.state()) || success != (data.succeededAt() != null)) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        }
        if ("SUCCEEDED".equals(data.state()) && data.succeededMinor() != data.amountMinor()
                || "FAILED".equals(data.state()) && data.failedMinor() != data.amountMinor()
                || "PARTIAL_SUCCEEDED".equals(data.state()) && (data.succeededMinor() == 0 || data.failedMinor() == 0)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private HttpProperties.Client configuration(MerchantNotificationProperties.Target target, int timeout) {
        var settings = httpProperties.getIfAvailable();
        var client = settings == null ? null : settings.clients().get(target.client());
        var operation = client == null ? null : client.operations().get(target.operation());
        if (client == null || operation == null || operation.method() != HttpMethod.POST || client.allowPrivateAddresses()
                || client.credentialRef() != null || client.proxy() != null || client.maxResponseBytes() > 4096
                || client.maxRequestBytes() < 65536 || client.connectTimeout().toMillis() + client.acquireTimeout().toMillis()
                + client.readTimeout().toMillis() > timeout) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return client;
    }

    private String checksum(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private MerchantNotificationProperties.Target target(String tenant, MerchantNotificationDelivery delivery) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var target = properties.targets().getOrDefault(tenant, Map.of()).get(delivery.policy().targetRef());
        if (target == null || !delivery.payeeMerchantId().equals(target.payeeMerchantId())) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
        return target;
    }

    private MerchantNotificationProperties.Signing signing(String tenant, MerchantNotificationDelivery delivery) {
        var identity = properties.signingIdentities().getOrDefault(tenant, Map.of()).get(delivery.policy().signingIdentityRef());
        if (identity == null || !delivery.payeeMerchantId().equals(identity.payeeMerchantId())) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
        return identity;
    }
}
