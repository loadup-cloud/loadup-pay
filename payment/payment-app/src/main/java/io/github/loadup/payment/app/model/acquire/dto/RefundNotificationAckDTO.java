package io.github.loadup.payment.app.model.acquire.dto;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/**
 * Acknowledges durable notification registration or the original disabled policy, never merchant delivery.
 */
public record RefundNotificationAckDTO(
        @Schema(description = "Original internal delivery id")
        String deliveryId,

        @Schema(description = "Original source business event id")
        String businessEventId,

        @Schema(description = "Original child or request aggregate id")
        String aggregateId,

        @Schema(description = "Exact terminal source version")
        long sourceVersion,

        @Schema(description = "Stable merchant event id") String merchantEventId,
        @Schema(description = "ENQUEUED or DISABLED") String disposition) {
    public RefundNotificationAckDTO {
        DomainChecks.id(deliveryId);
        DomainChecks.id(businessEventId);
        DomainChecks.id(aggregateId);
        require(
                sourceVersion > 0
                        && merchantEventId != null
                        && merchantEventId.matches("[0-9a-f]{64}")
                        && disposition != null
                        && Set.of("ENQUEUED", "DISABLED").contains(disposition),
                INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
