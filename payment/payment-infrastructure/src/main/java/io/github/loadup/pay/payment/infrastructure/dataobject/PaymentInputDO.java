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

import io.github.loadup.commons.dataobject.BaseDO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("payment_input")
public class PaymentInputDO extends BaseDO {
    private String paymentId;
    private String orderId;
    private String allocationId;
    private String payeeMerchantId;
    private Long amountMinor;
    private String currency;
    private String inputKey;
    private String channel;
    private String method;
    private String callerServiceId;
    private String fingerprintKeyId;
    @DiagnosticHidden
    private String inputDigest;
    private String encryptionKey;
    private Integer encryptionVersion;
    @DiagnosticHidden
    private String ciphertext;
    private LocalDateTime preparedAt;
    private LocalDateTime expiresAt;
    private String state;
    private String boundAttemptId;
    private Long rowVersion;

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String value) {
        paymentId = value;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String value) {
        orderId = value;
    }

    public String getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(String value) {
        allocationId = value;
    }

    public String getPayeeMerchantId() {
        return payeeMerchantId;
    }

    public void setPayeeMerchantId(String value) {
        payeeMerchantId = value;
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

    public String getInputKey() {
        return inputKey;
    }

    public void setInputKey(String value) {
        inputKey = value;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String value) {
        channel = value;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String value) {
        method = value;
    }

    public String getCallerServiceId() {
        return callerServiceId;
    }

    public void setCallerServiceId(String value) {
        callerServiceId = value;
    }

    public String getFingerprintKeyId() {
        return fingerprintKeyId;
    }

    public void setFingerprintKeyId(String value) {
        fingerprintKeyId = value;
    }

    public String getInputDigest() {
        return inputDigest;
    }

    public void setInputDigest(String value) {
        inputDigest = value;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String value) {
        encryptionKey = value;
    }

    public Integer getEncryptionVersion() {
        return encryptionVersion;
    }

    public void setEncryptionVersion(Integer value) {
        encryptionVersion = value;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String value) {
        ciphertext = value;
    }

    public LocalDateTime getPreparedAt() {
        return preparedAt;
    }

    public void setPreparedAt(LocalDateTime value) {
        preparedAt = value;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime value) {
        expiresAt = value;
    }

    public String getState() {
        return state;
    }

    public void setState(String value) {
        state = value;
    }

    public String getBoundAttemptId() {
        return boundAttemptId;
    }

    public void setBoundAttemptId(String value) {
        boundAttemptId = value;
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
