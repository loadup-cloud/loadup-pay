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
package io.github.loadup.pay.reconciliation.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.ToStringAsJson;

@Table("reconciliation_input")
public class ReconciliationInputDO extends BaseDO {
    private String side;
    private String channel;
    private String accountScopeHash;
    private java.time.LocalDate billDate;
    private String statementProfile;
    private String fileId;
    private String artifactSha256;
    private String normalizedSha256;
    private String sourceCaptureId;
    private String parserRevision;
    private String state;
    private Long paymentCount;
    private Long paymentAmountMinor;
    private Long refundCount;
    private Long refundAmountMinor;
    private String rejectionCode;
    private Long rowVersion;

    public String getSide() {
        return side;
    }

    public void setSide(String value) {
        side = value;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String value) {
        channel = value;
    }

    public String getAccountScopeHash() {
        return accountScopeHash;
    }

    public void setAccountScopeHash(String value) {
        accountScopeHash = value;
    }

    public java.time.LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(java.time.LocalDate value) {
        billDate = value;
    }

    public String getStatementProfile() {
        return statementProfile;
    }

    public void setStatementProfile(String value) {
        statementProfile = value;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String value) {
        fileId = value;
    }

    public String getArtifactSha256() {
        return artifactSha256;
    }

    public void setArtifactSha256(String value) {
        artifactSha256 = value;
    }

    public String getNormalizedSha256() {
        return normalizedSha256;
    }

    public void setNormalizedSha256(String value) {
        normalizedSha256 = value;
    }

    public String getSourceCaptureId() {
        return sourceCaptureId;
    }

    public void setSourceCaptureId(String value) {
        sourceCaptureId = value;
    }

    public String getParserRevision() {
        return parserRevision;
    }

    public void setParserRevision(String value) {
        parserRevision = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public Long getPaymentCount() {
        return paymentCount;
    }

    public void setPaymentCount(Long value) {
        paymentCount = value;
    }

    public Long getPaymentAmountMinor() {
        return paymentAmountMinor;
    }

    public void setPaymentAmountMinor(Long value) {
        paymentAmountMinor = value;
    }

    public Long getRefundCount() {
        return refundCount;
    }

    public void setRefundCount(Long value) {
        refundCount = value;
    }

    public Long getRefundAmountMinor() {
        return refundAmountMinor;
    }

    public void setRefundAmountMinor(Long value) {
        refundAmountMinor = value;
    }

    public String getRejectionCode() {
        return rejectionCode;
    }

    public void setRejectionCode(String value) {
        rejectionCode = value;
    }

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
