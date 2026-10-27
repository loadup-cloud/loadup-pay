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
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.json.DiagnosticHidden;

@Table("payment_reconciliation_line")
public class PaymentCaptureLineDO extends BaseDO {
    private String captureId;
    private Long ordinal;
    private String keyHash;
    private String kind;
    @DiagnosticHidden
    private String channelTransactionNo;
    @DiagnosticHidden
    private String channelRefundNo;
    @DiagnosticHidden
    private String merchantPaymentNo;
    private Long amountMinor;
    private String currency;
    private String status;
    private java.time.LocalDateTime sourceAt;
    private String sourceId;
    private String evidenceId;
    private Long sourceVersion;
    public String getCaptureId() { return captureId; }
    public void setCaptureId(String value) { captureId = value; }
    public Long getOrdinal() { return ordinal; }
    public void setOrdinal(Long value) { ordinal = value; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String value) { keyHash = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }
    public String getChannelTransactionNo() { return channelTransactionNo; }
    public void setChannelTransactionNo(String value) { channelTransactionNo = value; }
    public String getChannelRefundNo() { return channelRefundNo; }
    public void setChannelRefundNo(String value) { channelRefundNo = value; }
    public String getMerchantPaymentNo() { return merchantPaymentNo; }
    public void setMerchantPaymentNo(String value) { merchantPaymentNo = value; }
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long value) { amountMinor = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public java.time.LocalDateTime getSourceAt() { return sourceAt; }
    public void setSourceAt(java.time.LocalDateTime value) { sourceAt = value; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String value) { sourceId = value; }
    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String value) { evidenceId = value; }
    public Long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Long value) { sourceVersion = value; }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
