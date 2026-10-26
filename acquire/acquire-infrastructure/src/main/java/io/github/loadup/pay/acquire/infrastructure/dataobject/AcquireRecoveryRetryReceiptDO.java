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
package io.github.loadup.pay.acquire.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

@Table("acquire_recovery_retry_receipt")
public class AcquireRecoveryRetryReceiptDO extends BaseDO {
    private String requestKey;
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }

    private String callerScopeHash;
    public String getCallerScopeHash() { return callerScopeHash; }
    public void setCallerScopeHash(String value) { callerScopeHash = value; }

    private String callerServiceId;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }

    private String requestDigest;
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }

    private String kind;
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }

    private String subjectId;
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String value) { subjectId = value; }

    private Long generation;
    public Long getGeneration() { return generation; }
    public void setGeneration(Long value) { generation = value; }

    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String nativeTaskId;
    public String getNativeTaskId() { return nativeTaskId; }
    public void setNativeTaskId(String value) { nativeTaskId = value; }

    private Integer expectedAttempts;
    public Integer getExpectedAttempts() { return expectedAttempts; }
    public void setExpectedAttempts(Integer value) { expectedAttempts = value; }

    private java.time.LocalDateTime expectedNextExecuteAt;
    public java.time.LocalDateTime getExpectedNextExecuteAt() { return expectedNextExecuteAt; }
    public void setExpectedNextExecuteAt(java.time.LocalDateTime value) { expectedNextExecuteAt = value; }

    @DiagnosticHidden
    private String reason;
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }

    private String result;
    public String getResult() { return result; }
    public void setResult(String value) { result = value; }

    private java.time.LocalDateTime acceptedAt;
    public java.time.LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(java.time.LocalDateTime value) { acceptedAt = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
