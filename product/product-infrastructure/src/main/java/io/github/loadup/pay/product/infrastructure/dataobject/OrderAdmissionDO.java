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
package io.github.loadup.pay.product.infrastructure.dataobject;

import com.mybatisflex.annotation.Table;
import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;

@Table("order_admission")
public class OrderAdmissionDO extends BaseDO {
    private String orderId;
    private String payeeMerchantId;
    private String productScopeKey;
    private String requestDigest;
    private Long amountMinor;
    private String currency;
    private LocalDateTime requestedDueAt;
    private String callerScopeHash;
    private String contractId;
    private Integer contractRevision;
    private Long contractGeneration;
    private Long merchantFactVersion;
    private Long authorizationVersion;
    private String state;
    private String reasonCode;
    private LocalDateTime admittedAt;
    private LocalDateTime materializeBefore;
    private String snapshotHash;
    private String compiledTermsHash;
    @DiagnosticHidden
    private String compiledTerms;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
    }

    public String getProductScopeKey() {
        return productScopeKey;
    }

    public void setProductScopeKey(String value) {
        productScopeKey = value;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String value) {
        requestDigest = value;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long value) {
        amountMinor = value;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    public LocalDateTime getRequestedDueAt() {
        return requestedDueAt;
    }

    public void setRequestedDueAt(LocalDateTime value) {
        requestedDueAt = value;
    }

    public String getCallerScopeHash() {
        return callerScopeHash;
    }

    public void setCallerScopeHash(String value) {
        callerScopeHash = value;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String value) {
        contractId = value;
    }

    public Integer getContractRevision() {
        return contractRevision;
    }

    public void setContractRevision(Integer value) {
        contractRevision = value;
    }

    public Long getContractGeneration() {
        return contractGeneration;
    }

    public void setContractGeneration(Long value) {
        contractGeneration = value;
    }

    public Long getMerchantFactVersion() {
        return merchantFactVersion;
    }

    public void setMerchantFactVersion(Long value) {
        merchantFactVersion = value;
    }

    public Long getAuthorizationVersion() {
        return authorizationVersion;
    }

    public void setAuthorizationVersion(Long value) {
        authorizationVersion = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String value) {
        reasonCode = value;
    }

    public LocalDateTime getAdmittedAt() {
        return admittedAt;
    }

    public void setAdmittedAt(LocalDateTime value) {
        admittedAt = value;
    }

    public LocalDateTime getMaterializeBefore() {
        return materializeBefore;
    }

    public void setMaterializeBefore(LocalDateTime value) {
        materializeBefore = value;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String value) {
        snapshotHash = value;
    }

    public String getCompiledTermsHash() {
        return compiledTermsHash;
    }

    public void setCompiledTermsHash(String value) {
        compiledTermsHash = value;
    }

    public String getCompiledTerms() {
        return compiledTerms;
    }

    public void setCompiledTerms(String value) {
        compiledTerms = value;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
