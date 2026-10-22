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
package io.github.loadup.pay.payment.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.*;
import java.time.LocalDateTime;

@Table("channel_binding_change_receipt")
public class ChannelBindingChangeReceiptDO extends BaseDO {
    private String bindingId;
    public String getBindingId() { return bindingId; }
    public void setBindingId(String value) { bindingId = value; }

    private String kind;
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }

    private String requestKey;
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }

    private String requestDigest;
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }

    private String callerServiceId;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }

    private String bindingRevisionId;
    public String getBindingRevisionId() { return bindingRevisionId; }
    public void setBindingRevisionId(String value) { bindingRevisionId = value; }

    private Integer revision;
    public Integer getRevision() { return revision; }
    public void setRevision(Integer value) { revision = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private Long appliedVersion;
    public Long getAppliedVersion() { return appliedVersion; }
    public void setAppliedVersion(Long value) { appliedVersion = value; }

    private LocalDateTime appliedAt;
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime value) { appliedAt = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
