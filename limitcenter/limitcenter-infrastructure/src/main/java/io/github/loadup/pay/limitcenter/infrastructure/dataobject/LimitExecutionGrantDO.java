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
package io.github.loadup.pay.limitcenter.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

@Table("limit_execution_grant")
public class LimitExecutionGrantDO extends BaseDO {
    private String operationId;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String value) {
        operationId = value;
    }

    private String paymentId;

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    private String attemptId;

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String value) {
        attemptId = value;
    }

    private Long executionEpoch;

    public Long getExecutionEpoch() {
        return executionEpoch;
    }

    public void setExecutionEpoch(Long value) {
        executionEpoch = value;
    }

    private String requestKey;

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String value) {
        requestKey = value;
    }

    private String permitKeyId;

    public String getPermitKeyId() {
        return permitKeyId;
    }

    public void setPermitKeyId(String value) {
        permitKeyId = value;
    }

    @DiagnosticHidden
    private String permitHash;

    public String getPermitHash() {
        return permitHash;
    }

    public void setPermitHash(String value) {
        permitHash = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
