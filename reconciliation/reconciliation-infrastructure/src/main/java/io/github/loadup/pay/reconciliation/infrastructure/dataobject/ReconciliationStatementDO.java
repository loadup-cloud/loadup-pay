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

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

public class ReconciliationStatementDO extends BaseDO {
    private String batchId;
    private String callerServiceId;
    private String requestKey;
    private String requestDigest;
    private String channel;
    private String accountScopeHash;
    private java.time.LocalDate billDate;
    private String statementProfile;
    @DiagnosticHidden
    private String statementMerchantId;
    @DiagnosticHidden
    private String statementSubMerchantId;
    private String bindingRevisionId;
    private Integer maxBytes;
    private String taskId;
    private String state;
    private Long rowVersion;
    private String format;
    private String requestSha256;
    private String officialHashType;
    private String officialHash;
    private Long receivedEpochMicros;
    private Long downloadedEpochMicros;
    private String fileId;
    @DiagnosticHidden
    private String storageId;
    private Long fileSize;
    private String sourceSha256;
    private String proofFileId;
    @DiagnosticHidden
    private String proofStorageId;
    private Long proofFileSize;
    private String proofSha256;

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String value) {
        batchId = value;
    }

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String value) {
        requestKey = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
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

    public String getStatementMerchantId() {
        return statementMerchantId;
    }

    public void setStatementMerchantId(String value) {
        statementMerchantId = value;
    }

    public String getStatementSubMerchantId() {
        return statementSubMerchantId;
    }

    public void setStatementSubMerchantId(String value) {
        statementSubMerchantId = value;
    }

    public String getBindingRevisionId() {
        return bindingRevisionId;
    }

    public void setBindingRevisionId(String value) {
        bindingRevisionId = value;
    }

    public Integer getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(Integer value) {
        maxBytes = value;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String value) {
        taskId = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String value) {
        format = value;
    }

    public String getRequestSha256() {
        return requestSha256;
    }

    public void setRequestSha256(String value) {
        requestSha256 = value;
    }

    public String getOfficialHashType() {
        return officialHashType;
    }

    public void setOfficialHashType(String value) {
        officialHashType = value;
    }

    public String getOfficialHash() {
        return officialHash;
    }

    public void setOfficialHash(String value) {
        officialHash = value;
    }

    public Long getReceivedEpochMicros() {
        return receivedEpochMicros;
    }

    public void setReceivedEpochMicros(Long value) {
        receivedEpochMicros = value;
    }

    public Long getDownloadedEpochMicros() {
        return downloadedEpochMicros;
    }

    public void setDownloadedEpochMicros(Long value) {
        downloadedEpochMicros = value;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String value) {
        fileId = value;
    }

    public String getStorageId() {
        return storageId;
    }

    public void setStorageId(String value) {
        storageId = value;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long value) {
        fileSize = value;
    }

    public String getSourceSha256() {
        return sourceSha256;
    }

    public void setSourceSha256(String value) {
        sourceSha256 = value;
    }

    public String getProofFileId() {
        return proofFileId;
    }

    public void setProofFileId(String value) {
        proofFileId = value;
    }

    public String getProofStorageId() {
        return proofStorageId;
    }

    public void setProofStorageId(String value) {
        proofStorageId = value;
    }

    public Long getProofFileSize() {
        return proofFileSize;
    }

    public void setProofFileSize(Long value) {
        proofFileSize = value;
    }

    public String getProofSha256() {
        return proofSha256;
    }

    public void setProofSha256(String value) {
        proofSha256 = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
