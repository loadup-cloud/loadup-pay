package io.github.loadup.pay.payment.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PaymentHttpDefaultsTest {
    @Test
    void bindsActualPaymentSettingsWithoutPerTargetProperties() {
        var environment = new MockEnvironment().withProperty("spring.application.name", "payment");
        var settings = new PaymentPersistenceConfiguration().paymentLimitProperties(environment);
        assertThat(settings.tenantId()).isEqualTo("__default__");
        assertThat(settings.serviceId()).isEqualTo("payment");
        assertThat(settings.audience()).isEqualTo("limitcenter");
    }

    @Test
    void invalidOverrideIdentifiesTheFieldWithoutDisclosingItsValue() {
        var environment = new MockEnvironment()
                .withProperty("spring.application.name", "payment")
                .withProperty("loadup.modules.payment.limitcenter.tenant-id", "");
        assertThatThrownBy(() -> new PaymentPersistenceConfiguration().paymentLimitProperties(environment))
                .hasRootCauseMessage("loadup.modules.payment.limitcenter.tenant-id is missing or blank");
    }
}
