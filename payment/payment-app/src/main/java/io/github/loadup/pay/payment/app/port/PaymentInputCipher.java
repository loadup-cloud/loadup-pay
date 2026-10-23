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
package io.github.loadup.pay.payment.app.port;

import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.domain.model.EncryptedPaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInput;

import java.time.Duration;

public interface PaymentInputCipher {
    String activeFingerprintKeyId();

    Duration lifetime();

    String newReference();

    String fingerprint(String tenant, String service, PaymentInputPrepareCommand command, String keyId);

    EncryptedPaymentInput encrypt(String tenant, String service, String inputRef, PaymentInputPrepareCommand command);

    PaymentInteractionDTO decrypt(String tenant, PaymentInput stored);
}
