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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSettlementFactsServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentAcceptanceConverter.class.getPackageName());
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final PaymentAcceptanceGateway store = mock(PaymentAcceptanceGateway.class);
    final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "acquire", Set.of("pay:payment:acquire-facts")));
    final AtomicReference<StoredPaymentAcceptance> source = new AtomicReference<>(AttemptFixtures.accepted(true));
    final PaymentSettlementFactsService service = new PaymentSettlementFactsService(actor::get, store, codec,
            context.getBean(PaymentAcceptanceConverter.class), new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions()),
            validation.getValidator(), "limitcenter", "acquire");
    @BeforeEach void setup() {
        when(store.find("tenant", "payment")).thenAnswer(c -> Optional.of(source.get()));
        when(store.lockParent("tenant", "order")).thenReturn(Optional.of(AttemptFixtures.gate(true)));
        when(store.lockPayment("tenant", "payment")).thenReturn(Optional.of(AttemptFixtures.gate(false)));
        when(codec.decode(any())).thenReturn(AttemptFixtures.command());
    }
    @AfterEach void close() { context.close(); validation.close(); }
    @Test void originalAcquireServiceCanReadOperationAndPayeeWithItsDedicatedScope() {
        var facts = service.paymentFacts(new IdQuery("payment"));
        assertEquals("operation", facts.limitOperationId()); assertEquals("merchant", facts.payeeMerchantId());
        assertEquals("payment", facts.payment().paymentId());
    }
    @Test void limitcenterKeepsItsDedicatedAuthorityScope() {
        actor.set(new PaymentActor("tenant", "limitcenter", Set.of("pay:payment:limit-facts")));
        assertEquals("operation", service.paymentFacts(new IdQuery("payment")).limitOperationId());
    }
    @Test void acquireScopeCannotBeUsedByAnotherService() {
        actor.set(new PaymentActor("tenant", "other-service", Set.of("pay:payment:acquire-facts")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.paymentFacts(new IdQuery("payment"))).getResultCode());
        verifyNoInteractions(store);
    }
    @Test void acquireMustOwnTheOriginalServiceAcceptance() {
        var old = source.get();
        source.set(new StoredPaymentAcceptance(old.payment(), old.admissionId(), old.requestDigest(), "different-acquire",
                old.intentPayload(), old.payloadHash(), old.terminalEvidenceId()));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.paymentFacts(new IdQuery("payment"))).getResultCode());
        verify(store, never()).lockParent(anyString(), anyString());
    }
    @Test void missingRoleScopeCannotBeReplacedByTheOtherServicesPermission() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:limit-facts")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.paymentFacts(new IdQuery("payment"))).getResultCode());
        verifyNoInteractions(store);
    }
}
