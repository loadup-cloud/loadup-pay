package io.github.loadup.pay.limitcenter.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LimitCenterHttpContractTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();

    @Test
    void releaseBeforeReservePreservesTheFenceAndAbsentReservation() {
        var release = new LimitReleaseDTO("operation", "payment", true, null, 9007199254740993L, "evidence");
        var decoded = json.readValue(json.writeValueAsBytes(release), LimitReleaseDTO.class);
        assertThat(decoded).isEqualTo(release);
        assertThat(decoded.reservation()).isNull();
        assertThat(decoded.paymentVersion()).isEqualTo(9007199254740993L);
    }

    @Test
    void executionPermitIsTransportedByApiJsonButHiddenFromDiagnostics() {
        var permit = new LimitExecutionPermitDTO("operation", "payment", "attempt", 42, "synthetic-permit");
        var decoded = json.readValue(json.writeValueAsBytes(permit), LimitExecutionPermitDTO.class);
        assertThat(decoded).isEqualTo(permit);
        assertThat(permit.toString()).doesNotContain("synthetic-permit");
    }

    @Test
    void activationAckRetainsTheOriginalPolicyIdentityAndMicrosecondInstant() {
        var activated = OffsetDateTime.parse("2026-11-02T12:30:00.123456+08:00");
        var ack = new LimitPolicyActivationDTO("policy", 1, "a".repeat(64), activated);
        var decoded = json.readValue(json.writeValueAsBytes(ack), LimitPolicyActivationDTO.class);
        assertThat(decoded).isEqualTo(ack);
        assertThat(decoded.activatedAt().toInstant()).isEqualTo(activated.toInstant());
    }
}
