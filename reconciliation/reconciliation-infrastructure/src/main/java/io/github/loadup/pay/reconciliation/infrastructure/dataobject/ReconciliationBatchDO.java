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

@Table("reconciliation_batch")
public class ReconciliationBatchDO extends BaseDO {
    private String callerServiceId;
    private String requestKey;
    private String requestDigest;
    private String channelAccountCode;
    private String channel;
    private String accountScopeHash;
    private java.time.LocalDate billDate;
    private String statementProfile;
    private String state;
    private String channelInputId;
    private String ledgerInputId;
    private String runId;
    private Long matchedCount;
    private Long differenceCount;
    private String resultHash;
    private Long rowVersion;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String value) { requestDigest = value; }
    public String getChannelAccountCode() { return channelAccountCode; }
    public void setChannelAccountCode(String value) { channelAccountCode = value; }
    public String getChannel() { return channel; }
    public void setChannel(String value) { channel = value; }
    public String getAccountScopeHash() { return accountScopeHash; }
    public void setAccountScopeHash(String value) { accountScopeHash = value; }
    public java.time.LocalDate getBillDate() { return billDate; }
    public void setBillDate(java.time.LocalDate value) { billDate = value; }
    public String getStatementProfile() { return statementProfile; }
    public void setStatementProfile(String value) { statementProfile = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public String getChannelInputId() { return channelInputId; }
    public void setChannelInputId(String value) { channelInputId = value; }
    public String getLedgerInputId() { return ledgerInputId; }
    public void setLedgerInputId(String value) { ledgerInputId = value; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId = value; }
    public Long getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Long value) { matchedCount = value; }
    public Long getDifferenceCount() { return differenceCount; }
    public void setDifferenceCount(Long value) { differenceCount = value; }
    public String getResultHash() { return resultHash; }
    public void setResultHash(String value) { resultHash = value; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
