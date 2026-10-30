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
package io.github.loadup.pay.channel.wechat.protocol;

import org.springframework.http.HttpMethod;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Fixed named operations; callers cannot supply arbitrary URLs or HTTP methods.
 */
public enum WechatMethod {
    DIRECT_JSAPI("direct-jsapi", HttpMethod.POST, "/v3/pay/transactions/jsapi"),
    PARTNER_JSAPI("partner-jsapi", HttpMethod.POST, "/v3/pay/partner/transactions/jsapi"),
    DIRECT_H5("direct-h5", HttpMethod.POST, "/v3/pay/transactions/h5"),
    PARTNER_H5("partner-h5", HttpMethod.POST, "/v3/pay/partner/transactions/h5"),
    DIRECT_QUERY("direct-query", HttpMethod.GET, "/v3/pay/transactions/out-trade-no/{out_trade_no}"),
    PARTNER_QUERY("partner-query", HttpMethod.GET, "/v3/pay/partner/transactions/out-trade-no/{out_trade_no}"),
    DIRECT_CLOSE("direct-close", HttpMethod.POST, "/v3/pay/transactions/out-trade-no/{out_trade_no}/close"),
    PARTNER_CLOSE("partner-close", HttpMethod.POST, "/v3/pay/partner/transactions/out-trade-no/{out_trade_no}/close"),
    REFUND("refund-create", HttpMethod.POST, "/v3/refund/domestic/refunds"),
    REFUND_QUERY("refund-query", HttpMethod.GET, "/v3/refund/domestic/refunds/{out_refund_no}"),
    TRADE_BILL("trade-bill", HttpMethod.GET, "/v3/bill/tradebill"),
    BILL_DOWNLOAD("bill-download", HttpMethod.GET, "/v3/billdownload/file");

    private final String operation;
    private final HttpMethod method;
    private final String path;

    WechatMethod(String operation, HttpMethod method, String path) {
        this.operation = operation;
        this.method = method;
        this.path = path;
    }

    public String operation() {
        return operation;
    }

    public HttpMethod method() {
        return method;
    }

    public String path() {
        return path;
    }

    public boolean matches(String actualPath) {
        if (actualPath == null) {
            return false;
        }
        String marker = this == REFUND_QUERY ? "{out_refund_no}" : "{out_trade_no}";
        int variable = path.indexOf(marker);
        if (variable < 0) {
            return path.equals(actualPath);
        }
        String prefix = path.substring(0, variable), suffix = path.substring(variable + marker.length());
        if (!actualPath.startsWith(prefix) || !actualPath.endsWith(suffix)
                || actualPath.length() <= prefix.length() + suffix.length()) {
            return false;
        }
        String encoded = actualPath.substring(prefix.length(), actualPath.length() - suffix.length());
        if (encoded.indexOf('/') >= 0 || encoded.indexOf('+') >= 0) {
            return false;
        }
        try {
            String value = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            return value.matches(this == REFUND_QUERY ? "[A-Za-z0-9_\\-|*@]{1,64}" : "[A-Za-z0-9_-]{1,32}");
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
