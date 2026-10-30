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
package io.github.loadup.pay.payment.infrastructure.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.kms.KmsCiphertext;
import io.github.loadup.components.kms.KmsKeyRef;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.payment.app.port.PaymentInputCipher;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.domain.model.EncryptedPaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import io.github.loadup.pay.payment.infrastructure.config.PaymentInputProperties;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INPUT_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class KmsPaymentInputCipher implements PaymentInputCipher {
    private final ObjectProvider<KmsTemplate> kms;
    private final PaymentInputProperties properties;
    private final JsonMapper mapper;
    private final Validator validator;
    private final SecureRandom random = new SecureRandom();

    public KmsPaymentInputCipher(ObjectProvider<KmsTemplate> kms, PaymentInputProperties properties,
                                 JsonMapper mapper, Validator validator) {
        this.kms = kms;
        this.properties = properties;
        this.validator = validator;
        this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    @Override
    public String activeFingerprintKeyId() {
        return properties.activeFingerprintKeyId();
    }

    @Override
    public Duration lifetime() {
        return Duration.ofSeconds(properties.lifetimeSeconds());
    }

    @Override
    public String newReference() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String fingerprint(String tenant, String service, PaymentInputPrepareCommand command, String keyId) {
        String secret = properties.fingerprintKeys().get(keyId);
        if (secret == null) {
            throw new CommonException(INPUT_UNAVAILABLE);
        }
        return DigestUtils.hmacSha256(mapper.writeValueAsString(new FingerprintPayload(
                "loadup-pay-input-v1", tenant, service, keyId, command)), secret);
    }

    @Override
    public EncryptedPaymentInput encrypt(String tenant, String service, String inputRef, PaymentInputPrepareCommand command) {
        requireNoTransaction();
        byte[] bytes = mapper.writeValueAsBytes(new CipherPayload(1, tenant, service, inputRef, command));
        try {
            var value = template().encrypt(KmsKeyRef.latest(properties.encryptionKey()), bytes);
            return new EncryptedPaymentInput(value.keyName(), value.version(), value.value());
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Override
    public PaymentInteractionDTO decrypt(String tenant, PaymentInput stored) {
        requireNoTransaction();
        if (!properties.encryptionKey().equals(stored.encryptionKey())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        byte[] bytes = template().decrypt(new KmsKeyRef(stored.encryptionKey(), stored.encryptionVersion()),
                new KmsCiphertext(stored.encryptionKey(), stored.encryptionVersion(), stored.ciphertext()));
        try {
            CipherPayload payload = mapper.readValue(bytes, CipherPayload.class);
            if (payload == null || payload.schemaVersion() != 1 || !tenant.equals(payload.tenant())
                    || !stored.id().equals(payload.inputRef()) || !stored.callerServiceId().equals(payload.service())
                    || payload.command() == null || !validator.validate(payload.command()).isEmpty()) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var command = payload.command();
            if (!stored.paymentId().equals(command.paymentId()) || !stored.orderId().equals(command.orderId())
                    || !stored.allocationId().equals(command.allocationId())
                    || !stored.payeeMerchantId().equals(command.payeeMerchantId())
                    || stored.amount().getCent() != command.amountMinor()
                    || !stored.amount().getCurrencyValue().equals(command.currency())
                    || !stored.inputKey().equals(command.inputKey()) || stored.channel() != command.choice().channel()
                    || stored.method() != command.choice().method()) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            String digest = fingerprint(tenant, stored.callerServiceId(), command, stored.fingerprintKeyId());
            if (!MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII), stored.inputDigest().getBytes(StandardCharsets.US_ASCII))) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return command.interaction();
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private KmsTemplate template() {
        var value = kms.getIfAvailable();
        if (value == null) {
            throw new CommonException(INPUT_UNAVAILABLE);
        }
        return value;
    }

    private void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
    }

    private record FingerprintPayload(String protocol, String tenant, String service, String keyId,
                                      @DiagnosticHidden PaymentInputPrepareCommand command) {
        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    private record CipherPayload(int schemaVersion, String tenant, String service, @DiagnosticHidden String inputRef,
                                 @DiagnosticHidden PaymentInputPrepareCommand command) {
        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }
}
