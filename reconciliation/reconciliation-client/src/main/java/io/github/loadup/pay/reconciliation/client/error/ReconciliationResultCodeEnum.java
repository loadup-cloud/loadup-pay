/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.reconciliation.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum ReconciliationResultCodeEnum implements ResultCode {
    INVALID_ARGUMENT("RECONCILIATION_INVALID_ARGUMENT", "Invalid argument"),
    INVALID_PAGE("RECONCILIATION_INVALID_PAGE", "Invalid page"),
    NOT_FOUND("RECONCILIATION_NOT_FOUND", "Reconciliation record not found"),
    INVALID_STATE("RECONCILIATION_INVALID_STATE", "Invalid reconciliation state"),
    INPUT_NOT_SEALED("RECONCILIATION_INPUT_NOT_SEALED", "Both reconciliation inputs must be sealed"),
    STATEMENT_NOT_READY("RECONCILIATION_STATEMENT_NOT_READY", "Official statement is being generated"),
    STATEMENT_NOT_FOUND("RECONCILIATION_STATEMENT_NOT_FOUND", "Official statement is unavailable; no empty source is inferred"),
    REVIEW_VERSION_CONFLICT("RECONCILIATION_REVIEW_VERSION_CONFLICT", "Review version or proposal changed"),
    REVIEW_CHECKER_REQUIRED("RECONCILIATION_REVIEW_CHECKER_REQUIRED", "An independent authorized service must decide the proposal"),
    IMPORT_IN_PROGRESS("RECONCILIATION_IMPORT_IN_PROGRESS", "A channel import is already in progress"),
    IMPORT_SUPERSEDED("RECONCILIATION_IMPORT_SUPERSEDED", "The batch channel input has changed"),
    INPUT_INVALID("RECONCILIATION_INPUT_INVALID", "Reconciliation input is invalid"),
    SCOPE_MISMATCH("RECONCILIATION_SCOPE_MISMATCH", "Reconciliation input scope mismatch"),
    IDEMPOTENCY_CONFLICT("RECONCILIATION_IDEMPOTENCY_CONFLICT", "Reconciliation request changed"),
    SNAPSHOT_INVALID("RECONCILIATION_SNAPSHOT_INVALID", "Stored reconciliation snapshot is invalid"),
    CONCURRENT_UPDATE("RECONCILIATION_CONCURRENT_UPDATE", "Concurrent reconciliation update"),
    ACTION_FORBIDDEN("RECONCILIATION_ACTION_FORBIDDEN", "Reconciliation operation is not authorized"),
    DEPENDENCY_UNAVAILABLE("RECONCILIATION_DEPENDENCY_UNAVAILABLE", "Reconciliation dependency is unavailable");

    private final String code;
    private final String message;

    ReconciliationResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getStatus() {
        return ResultStatusEnum.FAIL.getCode();
    }
}
