package io.github.loadup.reconciliation.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;

@Mapper(config = LoadUpMapStructConfig.class, subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface RemoteContractConverter {
    java.util.List<io.github.loadup.reconciliation.app.model.payment.dto.PaymentLedgerLineDTO> toLocalLedgerLines(
            java.util.Collection<io.github.loadup.payment.client.dto.PaymentLedgerLineDTO> values);

    io.github.loadup.payment.client.command.PaymentCaptureCreateCommand toRemote(
            io.github.loadup.reconciliation.app.model.payment.command.PaymentCaptureCreateCommand value);

    io.github.loadup.reconciliation.app.model.payment.command.PaymentCaptureCreateCommand toLocal(
            io.github.loadup.payment.client.command.PaymentCaptureCreateCommand value);

    io.github.loadup.payment.client.command.PaymentCaptureRetryCommand toRemote(
            io.github.loadup.reconciliation.app.model.payment.command.PaymentCaptureRetryCommand value);

    io.github.loadup.reconciliation.app.model.payment.command.PaymentCaptureRetryCommand toLocal(
            io.github.loadup.payment.client.command.PaymentCaptureRetryCommand value);

    io.github.loadup.payment.client.dto.PaymentCaptureDTO toRemote(
            io.github.loadup.reconciliation.app.model.payment.dto.PaymentCaptureDTO value);

    io.github.loadup.reconciliation.app.model.payment.dto.PaymentCaptureDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentCaptureDTO value);

    io.github.loadup.payment.client.dto.PaymentLedgerLineDTO toRemote(
            io.github.loadup.reconciliation.app.model.payment.dto.PaymentLedgerLineDTO value);

    io.github.loadup.reconciliation.app.model.payment.dto.PaymentLedgerLineDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentLedgerLineDTO value);

    io.github.loadup.payment.client.dto.PaymentStatementDTO toRemote(
            io.github.loadup.reconciliation.app.model.payment.dto.PaymentStatementDTO value);

    io.github.loadup.reconciliation.app.model.payment.dto.PaymentStatementDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentStatementDTO value);

    io.github.loadup.payment.client.dto.PaymentVerificationDTO toRemote(
            io.github.loadup.reconciliation.app.model.payment.dto.PaymentVerificationDTO value);

    io.github.loadup.reconciliation.app.model.payment.dto.PaymentVerificationDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentVerificationDTO value);

    io.github.loadup.payment.client.enums.PaymentCaptureBlocker toRemote(
            io.github.loadup.reconciliation.app.model.payment.enums.PaymentCaptureBlocker value);

    io.github.loadup.reconciliation.app.model.payment.enums.PaymentCaptureBlocker toLocal(
            io.github.loadup.payment.client.enums.PaymentCaptureBlocker value);

    io.github.loadup.payment.client.enums.PaymentCaptureState toRemote(
            io.github.loadup.reconciliation.app.model.payment.enums.PaymentCaptureState value);

    io.github.loadup.reconciliation.app.model.payment.enums.PaymentCaptureState toLocal(
            io.github.loadup.payment.client.enums.PaymentCaptureState value);

    io.github.loadup.payment.client.enums.PaymentLedgerKind toRemote(
            io.github.loadup.reconciliation.app.model.payment.enums.PaymentLedgerKind value);

    io.github.loadup.reconciliation.app.model.payment.enums.PaymentLedgerKind toLocal(
            io.github.loadup.payment.client.enums.PaymentLedgerKind value);

    io.github.loadup.payment.client.enums.PaymentLedgerStatus toRemote(
            io.github.loadup.reconciliation.app.model.payment.enums.PaymentLedgerStatus value);

    io.github.loadup.reconciliation.app.model.payment.enums.PaymentLedgerStatus toLocal(
            io.github.loadup.payment.client.enums.PaymentLedgerStatus value);

    io.github.loadup.payment.client.enums.PaymentVerificationDisposition toRemote(
            io.github.loadup.reconciliation.app.model.payment.enums.PaymentVerificationDisposition value);

    io.github.loadup.reconciliation.app.model.payment.enums.PaymentVerificationDisposition toLocal(
            io.github.loadup.payment.client.enums.PaymentVerificationDisposition value);

    io.github.loadup.payment.client.enums.PaymentVerificationOutcome toRemote(
            io.github.loadup.reconciliation.app.model.payment.enums.PaymentVerificationOutcome value);

    io.github.loadup.reconciliation.app.model.payment.enums.PaymentVerificationOutcome toLocal(
            io.github.loadup.payment.client.enums.PaymentVerificationOutcome value);

    io.github.loadup.payment.client.error.PaymentResultCodeEnum toRemote(
            io.github.loadup.reconciliation.app.model.payment.error.PaymentResultCodeEnum value);

    io.github.loadup.reconciliation.app.model.payment.error.PaymentResultCodeEnum toLocal(
            io.github.loadup.payment.client.error.PaymentResultCodeEnum value);

    io.github.loadup.payment.client.query.PaymentCaptureLinesQuery toRemote(
            io.github.loadup.reconciliation.app.model.payment.query.PaymentCaptureLinesQuery value);

    io.github.loadup.reconciliation.app.model.payment.query.PaymentCaptureLinesQuery toLocal(
            io.github.loadup.payment.client.query.PaymentCaptureLinesQuery value);

    io.github.loadup.payment.client.query.PaymentStatementQuery toRemote(
            io.github.loadup.reconciliation.app.model.payment.query.PaymentStatementQuery value);

    io.github.loadup.reconciliation.app.model.payment.query.PaymentStatementQuery toLocal(
            io.github.loadup.payment.client.query.PaymentStatementQuery value);

    io.github.loadup.payment.client.query.PaymentVerificationQuery toRemote(
            io.github.loadup.reconciliation.app.model.payment.query.PaymentVerificationQuery value);

    io.github.loadup.reconciliation.app.model.payment.query.PaymentVerificationQuery toLocal(
            io.github.loadup.payment.client.query.PaymentVerificationQuery value);

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.reconciliation.app.model.payment.dto.PaymentCaptureDTO>
            fromPaymentPaymentCaptureDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.payment.client.dto.PaymentCaptureDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.reconciliation.app.model.payment.dto.PaymentStatementDTO>
            fromPaymentPaymentStatementDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.payment.client.dto.PaymentStatementDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.reconciliation.app.model.payment.dto.PaymentVerificationDTO>
            fromPaymentPaymentVerificationDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.payment.client.dto.PaymentVerificationDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }
}
