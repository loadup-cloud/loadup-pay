package io.github.loadup.pay.payment.client.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.loadup.commons.result.PageInfo;
import io.github.loadup.commons.result.Result;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import java.util.Collection;

/** Reads the standard flat page envelope or an HTTP-200 failure before validating its result. */
@JsonIgnoreProperties({"traceId"})
public record PaymentCapturePageResponse(
        Result result, Collection<PaymentLedgerLineDTO> data, PageInfo pageInfo) {}
