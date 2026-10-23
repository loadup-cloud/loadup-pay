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
package io.github.loadup.pay.channel.alipay.protocol;

public enum AlipayMethod {
    PAY("alipay.trade.pay", "trade-pay"),
    CREATE("alipay.trade.create", "trade-create"),
    QUERY("alipay.trade.query", "trade-query"),
    CLOSE("alipay.trade.close", "trade-close"),
    REFUND("alipay.trade.refund", "trade-refund"),
    REFUND_QUERY("alipay.trade.fastpay.refund.query", "refund-query");
    private final String api;
    private final String operation;
    AlipayMethod(String api, String operation) { this.api = api; this.operation = operation; }
    public String api() { return api; }
    public String operation() { return operation; }
    public String responseNode() { return api.replace('.', '_') + "_response"; }
}
