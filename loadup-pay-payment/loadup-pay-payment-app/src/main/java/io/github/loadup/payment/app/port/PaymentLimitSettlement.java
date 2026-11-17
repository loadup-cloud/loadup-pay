package io.github.loadup.payment.app.port;

import io.github.loadup.payment.app.model.limitcenter.command.LimitConfirmCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitReleaseCommand;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReleaseDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReservationDTO;

public interface PaymentLimitSettlement {
    LimitReservationDTO confirm(LimitConfirmCommand command);

    LimitReleaseDTO release(LimitReleaseCommand command);
}
