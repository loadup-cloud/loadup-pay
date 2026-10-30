package io.github.loadup.pay.limitcenter.rpc;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO;
import io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitConfirmCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExpireCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitOperationQuery;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReserveCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRestoreDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRuleDTO;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(config=LoadUpMapStructConfig.class, componentModel="default", unmappedSourcePolicy=ReportingPolicy.IGNORE, collectionMappingStrategy=CollectionMappingStrategy.ADDER_PREFERRED, nullValueCheckStrategy=NullValueCheckStrategy.ALWAYS, uses={RpcScalarMapper.class})
public interface LimitCenterRpcMapper {
    public static final LimitCenterRpcMapper INSTANCE = (LimitCenterRpcMapper)Mappers.getMapper(LimitCenterRpcMapper.class);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="policyId", source="policyId"), @Mapping(target="policyVersion", source="policyVersion"), @Mapping(target="policyHash", source="policyHash"), @Mapping(target="payeeMerchantId", source="payeeMerchantId"), @Mapping(target="refundAmountRestore", source="refundAmountRestore"), @Mapping(target="refundCountRestore", source="refundCountRestore")})
    public WireLimitPolicyActivateCommand toWireLimitPolicyActivateCommand(LimitPolicyActivateCommand value);

    @Mapping(target="rules", source="rulesList")
    public LimitPolicyActivateCommand fromWireLimitPolicyActivateCommand(WireLimitPolicyActivateCommand value);

    @AfterMapping
    default public void appendLimitPolicyActivateCommand(LimitPolicyActivateCommand value, @MappingTarget WireLimitPolicyActivateCommand.Builder builder) {
        if (value.rules() != null) {
            for (LimitRuleDTO item : value.rules()) {
                builder.addRules(this.toWireLimitRuleDTO(item));
            }
        }
    }

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="ruleScopeId", source="ruleScopeId"), @Mapping(target="merchantId", source="merchantId"), @Mapping(target="metric", source="metric"), @Mapping(target="currency", source="currency"), @Mapping(target="window", source="window"), @Mapping(target="weekStart", source="weekStart"), @Mapping(target="threshold", source="threshold"), @Mapping(target="enforcementEnabled", source="enforcementEnabled"), @Mapping(target="meteringEnabled", source="meteringEnabled")})
    public WireLimitRuleDTO toWireLimitRuleDTO(LimitRuleDTO value);

    public LimitRuleDTO fromWireLimitRuleDTO(WireLimitRuleDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="policyId", source="policyId"), @Mapping(target="policyVersion", source="policyVersion"), @Mapping(target="policyHash", source="policyHash"), @Mapping(target="activatedAt", source="activatedAt")})
    public WireLimitPolicyActivationDTO toWireLimitPolicyActivationDTO(LimitPolicyActivationDTO value);

    public LimitPolicyActivationDTO fromWireLimitPolicyActivationDTO(WireLimitPolicyActivationDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="orderId", source="orderId"), @Mapping(target="payeeMerchantId", source="payeeMerchantId"), @Mapping(target="amountMinor", source="amountMinor"), @Mapping(target="currency", source="currency"), @Mapping(target="initiatedAt", source="initiatedAt"), @Mapping(target="policyId", source="policyId"), @Mapping(target="policyVersion", source="policyVersion"), @Mapping(target="policyHash", source="policyHash"), @Mapping(target="requestDigest", source="requestDigest")})
    public WireLimitReserveCommand toWireLimitReserveCommand(LimitReserveCommand value);

    public LimitReserveCommand fromWireLimitReserveCommand(WireLimitReserveCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="reservationId", source="reservationId"), @Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="state", source="state"), @Mapping(target="policyId", source="policyId"), @Mapping(target="policyVersion", source="policyVersion"), @Mapping(target="initiatedAt", source="initiatedAt"), @Mapping(target="version", source="version")})
    public WireLimitReservationDTO toWireLimitReservationDTO(LimitReservationDTO value);

    public LimitReservationDTO fromWireLimitReservationDTO(WireLimitReservationDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mapping(target="operationId", source="operationId")
    public WireLimitOperationQuery toWireLimitOperationQuery(LimitOperationQuery value);

    public LimitOperationQuery fromWireLimitOperationQuery(WireLimitOperationQuery value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="attemptId", source="attemptId"), @Mapping(target="requestKey", source="requestKey"), @Mapping(target="previousAttemptEvidenceId", source="previousAttemptEvidenceId")})
    public WireLimitBeginExecutionCommand toWireLimitBeginExecutionCommand(LimitBeginExecutionCommand value);

    public LimitBeginExecutionCommand fromWireLimitBeginExecutionCommand(WireLimitBeginExecutionCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="attemptId", source="attemptId"), @Mapping(target="executionEpoch", source="executionEpoch"), @Mapping(target="permit", source="permit")})
    public WireLimitExecutionPermitDTO toWireLimitExecutionPermitDTO(LimitExecutionPermitDTO value);

    public LimitExecutionPermitDTO fromWireLimitExecutionPermitDTO(WireLimitExecutionPermitDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="attemptId", source="attemptId"), @Mapping(target="executionEpoch", source="executionEpoch"), @Mapping(target="amountMinor", source="amountMinor"), @Mapping(target="currency", source="currency"), @Mapping(target="permit", source="permit")})
    public WireLimitExecutionVerifyCommand toWireLimitExecutionVerifyCommand(LimitExecutionVerifyCommand value);

    public LimitExecutionVerifyCommand fromWireLimitExecutionVerifyCommand(WireLimitExecutionVerifyCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="valid", source="valid"), @Mapping(target="reasonCode", source="reasonCode")})
    public WireLimitExecutionVerificationDTO toWireLimitExecutionVerificationDTO(LimitExecutionVerificationDTO value);

    public LimitExecutionVerificationDTO fromWireLimitExecutionVerificationDTO(WireLimitExecutionVerificationDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mapping(target="operationId", source="operationId")
    public WireLimitExpireCommand toWireLimitExpireCommand(LimitExpireCommand value);

    public LimitExpireCommand fromWireLimitExpireCommand(WireLimitExpireCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="paymentVersion", source="paymentVersion"), @Mapping(target="businessEventId", source="businessEventId"), @Mapping(target="successEvidenceId", source="successEvidenceId"), @Mapping(target="amountMinor", source="amountMinor"), @Mapping(target="currency", source="currency"), @Mapping(target="succeededAt", source="succeededAt")})
    public WireLimitConfirmCommand toWireLimitConfirmCommand(LimitConfirmCommand value);

    public LimitConfirmCommand fromWireLimitConfirmCommand(WireLimitConfirmCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="paymentVersion", source="paymentVersion"), @Mapping(target="businessEventId", source="businessEventId"), @Mapping(target="noCollectionEvidenceId", source="noCollectionEvidenceId"), @Mapping(target="reasonCode", source="reasonCode")})
    public WireLimitReleaseCommand toWireLimitReleaseCommand(LimitReleaseCommand value);

    public LimitReleaseCommand fromWireLimitReleaseCommand(WireLimitReleaseCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="fenced", source="fenced"), @Mapping(target="reservation", source="reservation"), @Mapping(target="paymentVersion", source="paymentVersion"), @Mapping(target="noCollectionEvidenceId", source="noCollectionEvidenceId")})
    public WireLimitReleaseDTO toWireLimitReleaseDTO(LimitReleaseDTO value);

    public LimitReleaseDTO fromWireLimitReleaseDTO(WireLimitReleaseDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="refundId", source="refundId"), @Mapping(target="refundVersion", source="refundVersion"), @Mapping(target="businessEventId", source="businessEventId"), @Mapping(target="refundAmountMinor", source="refundAmountMinor"), @Mapping(target="cumulativePaymentRefundedMinor", source="cumulativePaymentRefundedMinor"), @Mapping(target="currency", source="currency"), @Mapping(target="refundEvidenceId", source="refundEvidenceId")})
    public WireLimitRefundRestoreCommand toWireLimitRefundRestoreCommand(LimitRefundRestoreCommand value);

    public LimitRefundRestoreCommand fromWireLimitRefundRestoreCommand(WireLimitRefundRestoreCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="operationId", source="operationId"), @Mapping(target="paymentId", source="paymentId"), @Mapping(target="refundId", source="refundId"), @Mapping(target="refundVersion", source="refundVersion"), @Mapping(target="businessEventId", source="businessEventId"), @Mapping(target="refundEvidenceId", source="refundEvidenceId"), @Mapping(target="cumulativePaymentRefundedMinor", source="cumulativePaymentRefundedMinor"), @Mapping(target="verifiedPaymentRefundedMinor", source="verifiedPaymentRefundedMinor"), @Mapping(target="verifiedPaymentRefundVersion", source="verifiedPaymentRefundVersion"), @Mapping(target="result", source="result"), @Mapping(target="restoredAmountMinor", source="restoredAmountMinor"), @Mapping(target="restoredPaymentCount", source="restoredPaymentCount")})
    public WireLimitRestoreDTO toWireLimitRestoreDTO(LimitRestoreDTO value);

    public LimitRestoreDTO fromWireLimitRestoreDTO(WireLimitRestoreDTO value);
}
