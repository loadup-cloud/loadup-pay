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

@Table("channel_merchant_binding")
public class ChannelMerchantBindingDO extends BaseDO {
    private String payeeMerchantId;
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }

    private String bindingCode;
    public String getBindingCode() { return bindingCode; }
    public void setBindingCode(String value) { bindingCode = value; }

    private String channel;
    public String getChannel() { return channel; }
    public void setChannel(String value) { channel = value; }

    private String accessMode;
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String value) { accessMode = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private String currentRevisionId;
    public String getCurrentRevisionId() { return currentRevisionId; }
    public void setCurrentRevisionId(String value) { currentRevisionId = value; }

    private Integer currentRevision;
    public Integer getCurrentRevision() { return currentRevision; }
    public void setCurrentRevision(Integer value) { currentRevision = value; }

    private Integer priority;
    public Integer getPriority() { return priority; }
    public void setPriority(Integer value) { priority = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
