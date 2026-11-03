package io.github.loadup.pay.reconciliation.client.http;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchTaskDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface ReconciliationHttpService {
    String CREATE_PATH = "/api/internal/v1/reconciliation/batches/create";
    String ATTACH_PATH = "/api/internal/v1/reconciliation/batches/attach";
    String START_PATH = "/api/internal/v1/reconciliation/batches/start";
    String RETRY_PATH = "/api/internal/v1/reconciliation/batches/retry";
    String TASK_PATH = "/api/internal/v1/reconciliation/batches/task";
    String DETAIL_PATH = "/api/internal/v1/reconciliation/batches/detail";

    @PostExchange(CREATE_PATH)
    SuccessResponse<ReconciliationBatchDTO> create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ReconciliationBatchCreateCommand request);

    @PostExchange(ATTACH_PATH)
    SuccessResponse<ReconciliationBatchDTO> attach(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ReconciliationInputAttachCommand request);

    @PostExchange(START_PATH)
    SuccessResponse<ReconciliationBatchDTO> start(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ReconciliationBatchStartCommand request);

    @PostExchange(RETRY_PATH)
    SuccessResponse<ReconciliationBatchDTO> retry(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ReconciliationBatchRetryCommand request);

    @PostExchange(TASK_PATH)
    SuccessResponse<ReconciliationBatchTaskDTO> task(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery request);

    @PostExchange(DETAIL_PATH)
    SuccessResponse<ReconciliationBatchDTO> detail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery request);
}
