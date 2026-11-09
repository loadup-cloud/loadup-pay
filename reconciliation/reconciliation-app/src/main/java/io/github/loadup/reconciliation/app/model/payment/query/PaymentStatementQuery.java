package io.github.loadup.reconciliation.app.model.payment.query;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Privileged source read. The consumer freezes its import intent before requesting this download.
 */
public record PaymentStatementQuery(
        @NotBlank @Size(max = 64) String bindingRevisionId,
        @NotNull Channel channel,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String accountScopeHash,
        @NotNull LocalDate billDate,
        @NotBlank @Size(max = 64) String statementProfile,
        @Min(1) @Max(16777216) int maxBytes) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
