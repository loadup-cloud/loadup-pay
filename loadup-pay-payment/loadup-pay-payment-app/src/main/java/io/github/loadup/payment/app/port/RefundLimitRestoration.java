package io.github.loadup.payment.app.port;

import io.github.loadup.payment.app.model.limitcenter.command.LimitRefundRestoreCommand;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitRestoreDTO;

public interface RefundLimitRestoration {
    LimitRestoreDTO restoreRefund(LimitRefundRestoreCommand command);
}
