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

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;
import org.springframework.data.relational.core.mapping.Table;

@Table("limit_settlement_receipt")
public class LimitSettlementReceiptDO extends BaseDO {
    private String operationId;
    private String paymentId;
    private String kind;
    private String businessEventId;
    private String requestDigest;
    private Long paymentVersion;
    private String evidenceId;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String value) {
        operationId = value;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String value) {
        kind = value;
    }

    public String getBusinessEventId() {
        return businessEventId;
    }

    public void setBusinessEventId(String value) {
        businessEventId = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public Long getPaymentVersion() {
        return paymentVersion;
    }

    public void setPaymentVersion(Long value) {
        paymentVersion = value;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String value) {
        evidenceId = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
