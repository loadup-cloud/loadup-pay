package io.github.loadup.payment.app.port;

import io.github.loadup.payment.app.model.limitcenter.command.LimitBeginExecutionCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitExecutionVerifyCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitReserveCommand;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionPermitDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionVerificationDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReservationDTO;

public interface PaymentLimitExecution {
    LimitReservationDTO reserve(LimitReserveCommand command);

    LimitExecutionPermitDTO begin(LimitBeginExecutionCommand command);

    LimitExecutionVerificationDTO verify(LimitExecutionVerifyCommand command);
}
