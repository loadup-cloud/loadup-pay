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
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("channel_binding_revision")
public class ChannelBindingRevisionDO extends BaseDO {
    private String bindingId;
    private Integer revision;
    private String payeeMerchantId;
    private String channel;
    private String accessMode;
    private String accountScopeHash;
    @DiagnosticHidden
    private String credentialReference;
    @DiagnosticHidden
    private String credentialVersion;
    @DiagnosticHidden
    private String identityPayload;
    private String identityHash;
    private Integer paymentCodeEnabled;
    private Integer jsapiEnabled;
    private Integer h5Enabled;
    private String callerServiceId;
    private LocalDateTime publishedAt;

    public String getBindingId() {
        return bindingId;
    }

    public void setBindingId(String value) {
        bindingId = value;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer value) {
        revision = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String value) {
        channel = value;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String value) {
        accessMode = value;
    }

    public String getAccountScopeHash() {
        return accountScopeHash;
    }

    public void setAccountScopeHash(String value) {
        accountScopeHash = value;
    }

    public String getCredentialReference() {
        return credentialReference;
    }

    public void setCredentialReference(String value) {
        credentialReference = value;
    }

    public String getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(String value) {
        credentialVersion = value;
    }

    public String getIdentityPayload() {
        return identityPayload;
    }

    public void setIdentityPayload(String value) {
        identityPayload = value;
    }

    public String getIdentityHash() {
        return identityHash;
    }

    public void setIdentityHash(String value) {
        identityHash = value;
    }

    public Integer getPaymentCodeEnabled() {
        return paymentCodeEnabled;
    }

    public void setPaymentCodeEnabled(Integer value) {
        paymentCodeEnabled = value;
    }

    public Integer getJsapiEnabled() {
        return jsapiEnabled;
    }

    public void setJsapiEnabled(Integer value) {
        jsapiEnabled = value;
    }

    public Integer getH5Enabled() {
        return h5Enabled;
    }

    public void setH5Enabled(Integer value) {
        h5Enabled = value;
    }

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime value) {
        publishedAt = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
