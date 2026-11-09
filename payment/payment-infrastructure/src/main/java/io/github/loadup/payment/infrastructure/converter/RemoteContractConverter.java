package io.github.loadup.payment.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;

@Mapper(config = LoadUpMapStructConfig.class, subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface RemoteContractConverter {
    io.github.loadup.acquire.client.dto.FeeRefundAckDTO toRemote(
            io.github.loadup.payment.app.model.acquire.dto.FeeRefundAckDTO value);

    io.github.loadup.payment.app.model.acquire.dto.FeeRefundAckDTO toLocal(
            io.github.loadup.acquire.client.dto.FeeRefundAckDTO value);

    io.github.loadup.acquire.client.dto.PaymentOutcomeAckDTO toRemote(
            io.github.loadup.payment.app.model.acquire.dto.PaymentOutcomeAckDTO value);

    io.github.loadup.payment.app.model.acquire.dto.PaymentOutcomeAckDTO toLocal(
            io.github.loadup.acquire.client.dto.PaymentOutcomeAckDTO value);

    io.github.loadup.acquire.client.dto.RefundNotificationAckDTO toRemote(
            io.github.loadup.payment.app.model.acquire.dto.RefundNotificationAckDTO value);

    io.github.loadup.payment.app.model.acquire.dto.RefundNotificationAckDTO toLocal(
            io.github.loadup.acquire.client.dto.RefundNotificationAckDTO value);

    io.github.loadup.limitcenter.client.command.LimitBeginExecutionCommand toRemote(
            io.github.loadup.payment.app.model.limitcenter.command.LimitBeginExecutionCommand value);

    io.github.loadup.payment.app.model.limitcenter.command.LimitBeginExecutionCommand toLocal(
            io.github.loadup.limitcenter.client.command.LimitBeginExecutionCommand value);

    io.github.loadup.limitcenter.client.command.LimitConfirmCommand toRemote(
            io.github.loadup.payment.app.model.limitcenter.command.LimitConfirmCommand value);

    io.github.loadup.payment.app.model.limitcenter.command.LimitConfirmCommand toLocal(
            io.github.loadup.limitcenter.client.command.LimitConfirmCommand value);

    io.github.loadup.limitcenter.client.command.LimitExecutionVerifyCommand toRemote(
            io.github.loadup.payment.app.model.limitcenter.command.LimitExecutionVerifyCommand value);

    io.github.loadup.payment.app.model.limitcenter.command.LimitExecutionVerifyCommand toLocal(
            io.github.loadup.limitcenter.client.command.LimitExecutionVerifyCommand value);

    io.github.loadup.limitcenter.client.command.LimitRefundRestoreCommand toRemote(
            io.github.loadup.payment.app.model.limitcenter.command.LimitRefundRestoreCommand value);

    io.github.loadup.payment.app.model.limitcenter.command.LimitRefundRestoreCommand toLocal(
            io.github.loadup.limitcenter.client.command.LimitRefundRestoreCommand value);

    io.github.loadup.limitcenter.client.command.LimitReleaseCommand toRemote(
            io.github.loadup.payment.app.model.limitcenter.command.LimitReleaseCommand value);

    io.github.loadup.payment.app.model.limitcenter.command.LimitReleaseCommand toLocal(
            io.github.loadup.limitcenter.client.command.LimitReleaseCommand value);

    io.github.loadup.limitcenter.client.command.LimitReserveCommand toRemote(
            io.github.loadup.payment.app.model.limitcenter.command.LimitReserveCommand value);

    io.github.loadup.payment.app.model.limitcenter.command.LimitReserveCommand toLocal(
            io.github.loadup.limitcenter.client.command.LimitReserveCommand value);

    io.github.loadup.limitcenter.client.dto.LimitExecutionPermitDTO toRemote(
            io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionPermitDTO value);

    io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionPermitDTO toLocal(
            io.github.loadup.limitcenter.client.dto.LimitExecutionPermitDTO value);

    io.github.loadup.limitcenter.client.dto.LimitExecutionVerificationDTO toRemote(
            io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionVerificationDTO value);

    io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionVerificationDTO toLocal(
            io.github.loadup.limitcenter.client.dto.LimitExecutionVerificationDTO value);

    io.github.loadup.limitcenter.client.dto.LimitReleaseDTO toRemote(
            io.github.loadup.payment.app.model.limitcenter.dto.LimitReleaseDTO value);

    io.github.loadup.payment.app.model.limitcenter.dto.LimitReleaseDTO toLocal(
            io.github.loadup.limitcenter.client.dto.LimitReleaseDTO value);

    io.github.loadup.limitcenter.client.dto.LimitReservationDTO toRemote(
            io.github.loadup.payment.app.model.limitcenter.dto.LimitReservationDTO value);

    io.github.loadup.payment.app.model.limitcenter.dto.LimitReservationDTO toLocal(
            io.github.loadup.limitcenter.client.dto.LimitReservationDTO value);

    io.github.loadup.limitcenter.client.dto.LimitRestoreDTO toRemote(
            io.github.loadup.payment.app.model.limitcenter.dto.LimitRestoreDTO value);

    io.github.loadup.payment.app.model.limitcenter.dto.LimitRestoreDTO toLocal(
            io.github.loadup.limitcenter.client.dto.LimitRestoreDTO value);

    io.github.loadup.limitcenter.client.enums.LimitReservationState toRemote(
            io.github.loadup.payment.app.model.limitcenter.enums.LimitReservationState value);

    io.github.loadup.payment.app.model.limitcenter.enums.LimitReservationState toLocal(
            io.github.loadup.limitcenter.client.enums.LimitReservationState value);

    io.github.loadup.limitcenter.client.enums.LimitRestoreResult toRemote(
            io.github.loadup.payment.app.model.limitcenter.enums.LimitRestoreResult value);

    io.github.loadup.payment.app.model.limitcenter.enums.LimitRestoreResult toLocal(
            io.github.loadup.limitcenter.client.enums.LimitRestoreResult value);

    io.github.loadup.realtime.client.dto.ProjectionAckDTO toRemote(
            io.github.loadup.payment.app.model.realtime.dto.ProjectionAckDTO value);

    io.github.loadup.payment.app.model.realtime.dto.ProjectionAckDTO toLocal(
            io.github.loadup.realtime.client.dto.ProjectionAckDTO value);

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.acquire.dto.FeeRefundAckDTO>
            fromAcquireFeeRefundAckDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<io.github.loadup.acquire.client.dto.FeeRefundAckDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.acquire.dto.PaymentOutcomeAckDTO>
            fromAcquirePaymentOutcomeAckDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.acquire.client.dto.PaymentOutcomeAckDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.acquire.dto.RefundNotificationAckDTO>
            fromAcquireRefundNotificationAckDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.acquire.client.dto.RefundNotificationAckDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionPermitDTO>
            fromLimitcenterLimitExecutionPermitDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.limitcenter.client.dto.LimitExecutionPermitDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionVerificationDTO>
            fromLimitcenterLimitExecutionVerificationDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.limitcenter.client.dto.LimitExecutionVerificationDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.limitcenter.dto.LimitReleaseDTO>
            fromLimitcenterLimitReleaseDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.limitcenter.client.dto.LimitReleaseDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.limitcenter.dto.LimitReservationDTO>
            fromLimitcenterLimitReservationDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.limitcenter.client.dto.LimitReservationDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.limitcenter.dto.LimitRestoreDTO>
            fromLimitcenterLimitRestoreDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.limitcenter.client.dto.LimitRestoreDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default io.github.loadup.commons.result.SuccessResponse<
                    io.github.loadup.payment.app.model.realtime.dto.ProjectionAckDTO>
            fromRealtimeProjectionAckDTOSuccessResponse(
                    io.github.loadup.commons.result.SuccessResponse<
                                    io.github.loadup.realtime.client.dto.ProjectionAckDTO>
                            response) {
        return response == null
                ? null
                : new io.github.loadup.commons.result.SuccessResponse<>(response.result(), toLocal(response.data()));
    }
}
