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
package io.github.loadup.pay.reconciliation.infrastructure.statement;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.*;

/** Strict API ALL-format parser. Content completeness never proves channel provenance or authorizes ledger imports. */
@Component
public class WechatTradeStatementParser {
    public static final String PROFILE = "wechat-trade-all-20260116";
    public static final String HEADER = "交易时间,公众账号ID,商户号,特约商户号,设备号,微信订单号,商户订单号,用户标识,交易类型,交易状态,付款银行,货币种类,应结订单金额,代金券金额,微信退款单号,商户退款单号,退款金额,充值券退款金额,退款类型,退款状态,商品名称,商户数据包,手续费,费率,订单金额,申请退款金额,费率备注";
    public static final String TOTAL_HEADER = "总交易单数,应结订单总金额,退款总金额,充值券退款总金额,手续费总金额,订单总金额,申请退款总金额";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private final int maxRecords;
    private final int maxBytes;
    public WechatTradeStatementParser(@Value("${loadup.modules.reconciliation.max-records:10000}") int maxRecords,
            @Value("${loadup.modules.reconciliation.statement-max-bytes:16777216}") int maxBytes) {
        if (maxRecords < 1 || maxRecords > 1000000 || maxBytes < 1024 || maxBytes > 67108864) throw new CommonException(INVALID_ARGUMENT);
        this.maxRecords = maxRecords; this.maxBytes = maxBytes;
    }
    /** The caller owns the stream and must independently establish the source file's identity and provenance. */
    public ReconciliationDataset parse(InputStream source, ReconciliationScope scope, ReconciliationStatementAccount account,
            String artifactId, String sourceCaptureId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (source == null || scope == null || scope.channel() != Channel.WECHAT || !PROFILE.equals(scope.statementProfile()) || account == null || !account.merchantId().matches("[0-9]{8,10}")
                || account.subMerchantId() != null && !account.subMerchantId().matches("[0-9]{8,10}"))
            throw new CommonException(INPUT_INVALID);
        try {
            byte[] raw = source.readNBytes(maxBytes + 1);
            if (raw.length > maxBytes) throw new CommonException(INPUT_INVALID);
            String content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
            if (content.startsWith("\uFEFF")) content = content.substring(1);
            var reader = new BufferedReader(new StringReader(content));
            if (!HEADER.equals(reader.readLine())) throw new CommonException(INPUT_INVALID);
            var lines = new ArrayList<ReconciliationLine>();
            long netPayment = 0, netRefund = 0, couponRefund = 0, fees = 0, grossPayment = 0, grossRefund = 0;
            String line;
            while ((line = reader.readLine()) != null && !TOTAL_HEADER.equals(line)) {
                if (lines.size() >= maxRecords) throw new CommonException(INPUT_INVALID);
                var fields = fields(line, 27);
                if (!account.merchantId().equals(fields[2]) || !(account.subMerchantId() == null ? "0" : account.subMerchantId()).equals(fields[3])
                        || !"CNY".equals(fields[11]) || !LocalDateTime.parse(fields[0], TIME).toLocalDate().equals(scope.billDate()))
                    throw new CommonException(INPUT_INVALID);
                long orderNet = money(fields[12], false), refundNet = money(fields[16], false), refundCoupon = money(fields[17], false);
                long fee = money(fields[22], true), orderGross = money(fields[24], false), refundGross = money(fields[25], false);
                ReconciliationRecordKind kind; ReconciliationEntryStatus status; long amount; String refundNo;
                if ("SUCCESS".equals(fields[9])) {
                    if (orderGross <= 0 || refundGross != 0 || refundNet != 0 || refundCoupon != 0 || fee < 0
                            || !"0".equals(fields[14]) || !"0".equals(fields[15]) || !fields[18].isEmpty() || !fields[19].isEmpty())
                        throw new CommonException(INPUT_INVALID);
                    kind = ReconciliationRecordKind.PAYMENT; status = ReconciliationEntryStatus.SUCCEEDED; amount = orderGross; refundNo = null;
                } else if ("REFUND".equals(fields[9])) {
                    if (refundGross <= 0 || orderGross != 0 || orderNet != 0 || fee > 0 || "0".equals(fields[14]) || "0".equals(fields[15]))
                        throw new CommonException(INPUT_INVALID);
                    kind = ReconciliationRecordKind.REFUND; amount = refundGross; refundNo = id(fields[15], 64);
                    id(fields[14], 128);
                    status = switch (fields[19]) {
                        case "SUCCESS" -> ReconciliationEntryStatus.SUCCEEDED;
                        case "PROCESSING" -> ReconciliationEntryStatus.PROCESSING;
                        case "FAIL" -> ReconciliationEntryStatus.FAILED;
                        case "CHANGE" -> ReconciliationEntryStatus.UNKNOWN;
                        default -> throw new CommonException(INPUT_INVALID);
                    };
                } else {
                    // Revocation has a distinct compensation identity; never drop it or manufacture a child-refund number.
                    throw new CommonException(INPUT_INVALID);
                }
                if (orderNet > orderGross || refundNet > refundGross || refundCoupon > refundGross) throw new CommonException(INPUT_INVALID);
                lines.add(new ReconciliationLine(new ReconciliationKey(kind, id(fields[5], 128), refundNo), id(fields[6], 64),
                        Money.ofMinor(amount, "CNY"), status, lines.size() + 1L));
                netPayment = Math.addExact(netPayment, orderNet); netRefund = Math.addExact(netRefund, refundNet);
                couponRefund = Math.addExact(couponRefund, refundCoupon); fees = Math.addExact(fees, fee);
                grossPayment = Math.addExact(grossPayment, orderGross); grossRefund = Math.addExact(grossRefund, refundGross);
            }
            if (line == null) throw new CommonException(INPUT_INVALID);
            var totals = fields(reader.readLine(), 7);
            if (!totals[0].matches("0|[1-9][0-9]{0,18}") || Long.parseLong(totals[0]) != lines.size()
                    || money(totals[1], false) != netPayment || money(totals[2], false) != netRefund
                    || money(totals[3], false) != couponRefund || money(totals[4], true) != fees
                    || money(totals[5], false) != grossPayment || money(totals[6], false) != grossRefund || reader.readLine() != null)
                throw new CommonException(INPUT_INVALID);
            var manifest = new ReconciliationManifest(ReconciliationSide.CHANNEL, scope, artifactId,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)),
                    ReconciliationDataset.normalizedHash(scope, ReconciliationSide.CHANNEL, PROFILE, lines), sourceCaptureId, PROFILE,
                    ReconciliationTotals.count(lines));
            return new ReconciliationDataset(manifest, lines);
        } catch (CommonException failure) { throw failure; }
        catch (NoSuchAlgorithmException failure) { throw new CommonException(DEPENDENCY_UNAVAILABLE, failure); }
        catch (IOException | RuntimeException failure) { throw new CommonException(INPUT_INVALID); }
    }
    private String[] fields(String line, int count) {
        if (line == null || line.length() > 65536) throw new CommonException(INPUT_INVALID);
        var values = line.split(",", -1);
        if (values.length != count) throw new CommonException(INPUT_INVALID);
        for (int i = 0; i < values.length; i++) {
            if (!values[i].startsWith("`")) throw new CommonException(INPUT_INVALID);
            values[i] = values[i].substring(1);
        }
        return values;
    }
    private long money(String value, boolean signed) {
        if (!(signed ? value.matches("-?(?:0|[1-9][0-9]{0,16})\\.[0-9]{2}") : value.matches("(?:0|[1-9][0-9]{0,16})\\.[0-9]{2}")))
            throw new CommonException(INPUT_INVALID);
        return new BigDecimal(value).movePointRight(2).longValueExact();
    }
    private String id(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9_-]+")) throw new CommonException(INPUT_INVALID);
        return value;
    }
}
