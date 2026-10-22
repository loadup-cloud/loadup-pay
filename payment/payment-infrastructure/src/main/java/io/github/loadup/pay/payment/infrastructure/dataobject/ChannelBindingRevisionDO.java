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

@Table("channel_binding_revision")
public class ChannelBindingRevisionDO extends BaseDO {
    private String bindingId;
    public String getBindingId() { return bindingId; }
    public void setBindingId(String value) { bindingId = value; }

    private Integer revision;
    public Integer getRevision() { return revision; }
    public void setRevision(Integer value) { revision = value; }

    private String payeeMerchantId;
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }

    private String channel;
    public String getChannel() { return channel; }
    public void setChannel(String value) { channel = value; }

    private String accessMode;
    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String value) { accessMode = value; }

    private String accountScopeHash;
    public String getAccountScopeHash() { return accountScopeHash; }
    public void setAccountScopeHash(String value) { accountScopeHash = value; }

    @DiagnosticHidden
    private String credentialReference;
    public String getCredentialReference() { return credentialReference; }
    public void setCredentialReference(String value) { credentialReference = value; }

    @DiagnosticHidden
    private String credentialVersion;
    public String getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(String value) { credentialVersion = value; }

    @DiagnosticHidden
    private String identityPayload;
    public String getIdentityPayload() { return identityPayload; }
    public void setIdentityPayload(String value) { identityPayload = value; }

    private String identityHash;
    public String getIdentityHash() { return identityHash; }
    public void setIdentityHash(String value) { identityHash = value; }

    private Integer paymentCodeEnabled;
    public Integer getPaymentCodeEnabled() { return paymentCodeEnabled; }
    public void setPaymentCodeEnabled(Integer value) { paymentCodeEnabled = value; }

    private Integer jsapiEnabled;
    public Integer getJsapiEnabled() { return jsapiEnabled; }
    public void setJsapiEnabled(Integer value) { jsapiEnabled = value; }

    private Integer h5Enabled;
    public Integer getH5Enabled() { return h5Enabled; }
    public void setH5Enabled(Integer value) { h5Enabled = value; }

    private String callerServiceId;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }

    private LocalDateTime publishedAt;
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
