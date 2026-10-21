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
import io.github.loadup.commons.json.ToStringAsJson;

@Table("limit_operation_gate")
public class LimitOperationGateDO extends BaseDO {
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

    private String state;

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    private Long paymentSourceVersion;

    public Long getPaymentSourceVersion() {
        return paymentSourceVersion;
    }

    public void setPaymentSourceVersion(Long value) {
        paymentSourceVersion = value;
    }

    private String noCollectionEvidenceId;

    public String getNoCollectionEvidenceId() {
        return noCollectionEvidenceId;
    }

    public void setNoCollectionEvidenceId(String value) {
        noCollectionEvidenceId = value;
    }

    private Long rowVersion;

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
