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

@Table("payment_input")
public class PaymentInputDO extends BaseDO {
    private String paymentId;
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }

    private String orderId;
    public String getOrderId() { return orderId; }
    public void setOrderId(String value) { orderId = value; }

    private String allocationId;
    public String getAllocationId() { return allocationId; }
    public void setAllocationId(String value) { allocationId = value; }

    private String payeeMerchantId;
    public String getPayeeMerchantId() { return payeeMerchantId; }
    public void setPayeeMerchantId(String value) { payeeMerchantId = value; }

    private Long amountMinor;
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }

    private String currency;
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }

    private String inputKey;
    public String getInputKey() { return inputKey; }
    public void setInputKey(String value) { inputKey = value; }

    private String channel;
    public String getChannel() { return channel; }
    public void setChannel(String value) { channel = value; }

    private String method;
    public String getMethod() { return method; }
    public void setMethod(String value) { method = value; }

    private String callerServiceId;
    public String getCallerServiceId() { return callerServiceId; }
    public void setCallerServiceId(String value) { callerServiceId = value; }

    private String fingerprintKeyId;
    public String getFingerprintKeyId() { return fingerprintKeyId; }
    public void setFingerprintKeyId(String value) { fingerprintKeyId = value; }

    @DiagnosticHidden
    private String inputDigest;
    public String getInputDigest() { return inputDigest; }
    public void setInputDigest(String value) { inputDigest = value; }

    private String encryptionKey;
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String value) { encryptionKey = value; }

    private Integer encryptionVersion;
    public Integer getEncryptionVersion() { return encryptionVersion; }
    public void setEncryptionVersion(Integer value) { encryptionVersion = value; }

    @DiagnosticHidden
    private String ciphertext;
    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String value) { ciphertext = value; }

    private LocalDateTime preparedAt;
    public LocalDateTime getPreparedAt() { return preparedAt; }
    public void setPreparedAt(LocalDateTime value) { preparedAt = value; }

    private LocalDateTime expiresAt;
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { expiresAt = value; }

    private String state;
    public String getState() { return state; }
    public void setState(String value) { state = value; }

    private String boundAttemptId;
    public String getBoundAttemptId() { return boundAttemptId; }
    public void setBoundAttemptId(String value) { boundAttemptId = value; }

    private Long rowVersion;
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long value) { rowVersion = value; }

    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
