import test from "node:test";
import assert from "node:assert/strict";
import {createBridgePayments} from "../../main/resources/cashier/checkout-bridge.mjs";
import {invocation, newOperation, payment, persist} from "../../main/resources/cashier/checkout-core.mjs";

const wechat = {channel: "WECHAT", method: "JSAPI"}, alipay = {channel: "ALIPAY", method: "JSAPI"};
const epoch = Date.parse("2026-10-28T06:00:00Z");
function action(channel = "WECHAT", expiresAt = epoch + 120000) {
    return {type: "INVOKE_JSAPI", expiresAt: new Date(expiresAt).toISOString(), data: channel === "WECHAT"
        ? {type: "WECHAT_JSAPI", appId: "wx-app", timeStamp: "1793167200", nonceStr: "nonce", package: "prepay_id=private-prepay", signType: "RSA", paySign: "AQ=="}
        : {type: "ALIPAY_JSAPI", tradeNo: "private-original-trade"}};
}
function environment() {
    let time = epoch, sequence = 0;
    const timers = new Map(), listeners = new Map(), host = {};
    const document = {
        addEventListener(name, callback) { if (!listeners.has(name)) listeners.set(name, new Set()); listeners.get(name).add(callback); },
        removeEventListener(name, callback) { listeners.get(name)?.delete(callback); }
    };
    const sdk = createBridgePayments({window: host, document, now: () => time,
        setTimeout(callback, delay) { const key = ++sequence; timers.set(key, {callback, at: time + delay}); return key; },
        clearTimeout(key) { timers.delete(key); }});
    return {sdk, host, timers,
        emit(name) { for (const callback of [...(listeners.get(name) ?? [])]) callback(); },
        listenerCount() { return [...listeners.values()].reduce((sum, set) => sum + set.size, 0); },
        tick(ms) {
            time += ms;
            for (const [key, task] of [...timers.entries()].sort((a, b) => a[1].at - b[1].at)) {
                if (task.at <= time && timers.delete(key)) task.callback();
            }
        }};
}

test("WeChat invokes only exact server-owned RSA parameters and native success only requests a server refresh", async () => {
    const env = environment(); let call;
    env.host.WeixinJSBridge = {invoke(...args) { call = args; }};
    const pending = env.sdk.invoke(action(), wechat);
    assert.equal(call[0], "getBrandWCPayRequest");
    assert.deepEqual(call[1], {appId: "wx-app", timeStamp: "1793167200", nonceStr: "nonce", package: "prepay_id=private-prepay", signType: "RSA", paySign: "AQ=="});
    call[2]({err_msg: "get_brand_wcpay_request:ok", callbackUrl: "https://untrusted.example/", state: "SUCCEEDED"});
    assert.deepEqual(await pending, {status: "RETURNED", invoked: true});
    assert.equal(env.timers.size, 0); assert.equal(env.listenerCount(), 0);
});

test("Alipay preserves the original tradeNO and does not return wallet callback credentials or URLs", async () => {
    const env = environment(); let call;
    env.host.AlipayJSBridge = {call(...args) { call = args; }};
    const pending = env.sdk.invoke(action("ALIPAY"), alipay);
    assert.equal(call[0], "tradePay"); assert.deepEqual(call[1], {tradeNO: "private-original-trade"});
    call[2]({resultCode: "9000", callbackUrl: "https://untrusted.example", memo: "synthetic-secret"});
    assert.deepEqual(await pending, {status: "RETURNED", invoked: true});
});

test("delayed bridge readiness uses an immutable parameter copy and repeated readiness events never reinvoke", async () => {
    const env = environment(), original = action(); let count = 0, callback, parameters;
    const pending = env.sdk.invoke(original, wechat);
    original.data.package = "prepay_id=replaced"; original.data.paySign = "Ag==";
    env.host.WeixinJSBridge = {invoke(_method, data, result) { count++; parameters = data; callback = result; }};
    env.emit("WeixinJSBridgeReady"); env.emit("WeixinJSBridgeReady"); env.emit("WeixinJSBridgeReady");
    assert.equal(count, 1); assert.equal(parameters.package, "prepay_id=private-prepay"); assert.equal(parameters.paySign, "AQ==");
    callback({err_msg: "get_brand_wcpay_request:cancel"});
    assert.deepEqual(await pending, {status: "CANCELLED", invoked: true});
});

test("missing native bridge times out without loading a script and late readiness cannot invoke", async () => {
    const env = environment(); let count = 0;
    const pending = env.sdk.invoke(action(), wechat); env.tick(5000);
    assert.deepEqual(await pending, {status: "UNAVAILABLE", invoked: false});
    env.host.WeixinJSBridge = {invoke() { count++; }}; env.emit("WeixinJSBridgeReady");
    assert.equal(count, 0); assert.equal(env.listenerCount(), 0); assert.equal(env.timers.size, 0);
});

test("an action expiring while the bridge is absent can never be launched later", async () => {
    const env = environment(); let count = 0;
    const pending = env.sdk.invoke(action("WECHAT", epoch + 2000), wechat); env.tick(2000);
    env.host.WeixinJSBridge = {invoke() { count++; }}; env.emit("WeixinJSBridgeReady");
    assert.deepEqual(await pending, {status: "EXPIRED", invoked: false}); assert.equal(count, 0);
    assert.deepEqual(await env.sdk.invoke(action("WECHAT", epoch), wechat), {status: "EXPIRED", invoked: false});
});

test("callback timeout is UNKNOWN even if the action has since expired; late native success is not financial evidence", async () => {
    const env = environment(); let callback;
    env.host.WeixinJSBridge = {invoke(_method, _parameters, result) { callback = result; }};
    const pending = env.sdk.invoke(action("WECHAT", epoch + 1000), wechat); env.tick(60000);
    assert.deepEqual(await pending, {status: "UNKNOWN", invoked: true});
    callback({err_msg: "get_brand_wcpay_request:ok"});
    assert.deepEqual(await pending, {status: "UNKNOWN", invoked: true});
});

test("abort removes readiness listeners; abort after native invocation means unknown and never cancels a payment", async () => {
    const env = environment(), before = new AbortController();
    const waiting = env.sdk.invoke(action(), wechat, {signal: before.signal}); before.abort();
    assert.deepEqual(await waiting, {status: "ABORTED", invoked: false}); assert.equal(env.listenerCount(), 0);
    const after = new AbortController(); let callback;
    env.host.AlipayJSBridge = {call(_method, _parameters, result) { callback = result; }};
    const running = env.sdk.invoke(action("ALIPAY"), alipay, {signal: after.signal}); after.abort();
    assert.deepEqual(await running, {status: "UNKNOWN", invoked: true}); callback({resultCode: "9000"});
    assert.equal(env.timers.size, 0);
});

test("only one native invocation can be pending; an explicit later click may reuse the same original action", async () => {
    const env = environment(); let callback, count = 0;
    env.host.AlipayJSBridge = {call(_method, _parameters, result) { count++; callback = result; }};
    const first = env.sdk.invoke(action("ALIPAY"), alipay);
    await assert.rejects(env.sdk.invoke(action("ALIPAY"), alipay), error => error.code === "BRIDGE_BUSY");
    callback({resultCode: "6001"}); assert.equal((await first).status, "CANCELLED");
    const second = env.sdk.invoke(action("ALIPAY"), alipay); callback({resultCode: "6001"});
    assert.equal((await second).status, "CANCELLED"); assert.equal(count, 2);
});

test("native invocation throwing after entry leaves UNKNOWN and does not leak its exception text", async () => {
    const env = environment(); env.host.AlipayJSBridge = {call() { throw new Error("synthetic-secret"); }};
    assert.deepEqual(await env.sdk.invoke(action("ALIPAY"), alipay), {status: "UNKNOWN", invoked: true});
    assert.equal(env.listenerCount(), 0); assert.equal(env.timers.size, 0);
});

test("wrong channel, browser numeric timestamps, extra credentials and malformed RSA fields fail before native calls", async () => {
    const env = environment(); let count = 0;
    env.host.WeixinJSBridge = {invoke() { count++; }};
    const invalid = [action("ALIPAY"), {...action(), data: {...action().data, timeStamp: 1793167200}},
        {...action(), data: {...action().data, app_auth_token: "synthetic-secret"}},
        {...action(), data: {...action().data, signType: "MD5"}}, {...action(), data: {...action().data, package: "prepay_id="}},
        {...action(), data: {...action().data, nonceStr: "bad\nnonce"}}, {...action(), data: {...action().data, paySign: "bad signature"}}];
    for (const value of invalid) await assert.rejects(env.sdk.invoke(value, wechat));
    await assert.rejects(env.sdk.invoke(action(), {channel: "WECHAT", method: "H5"}));
    assert.equal(count, 0); assert.equal(env.listenerCount(), 0);
});

test("signed action details never enter persisted operation metadata", () => {
    const operation = newOperation("order", 100n, wechat, "fixed-action"); let text;
    persist({setItem(_key, value) { text = value; }, getItem() { return text; }}, "key", {...operation, action: action()});
    assert.equal(text.includes("prepay_id="), false); assert.equal(text.includes("paySign"), false);
    assert.deepEqual(JSON.parse(text).choice, wechat);
    const resolved = invocation(action(), wechat, epoch);
    assert.equal(resolved.parameters.timeStamp, "1793167200"); assert.equal(Object.isFrozen(resolved.parameters), true);
});


test("checkout payment actions remain bound to their original channel and method", () => {
    const operation = newOperation("order", 100n, wechat, "fixed-action");
    const summary = {orderId: "order", subject: "order", currency: "CNY", state: "PENDING", amountMinor: 100n,
        paidAmountMinor: 0n, paymentReservedMinor: 100n, availableToPayMinor: 0n, minimumPaymentMinor: 1n,
        maximumPaymentMinor: 100n, allowExactRemainderBelowMin: true, remainingPaymentCount: 1n, choices: [wechat]};
    const response = {orderId: "order", paymentId: "payment", accepted: true, state: "PROCESSING", version: 1n, order: summary, action: action()};
    assert.equal(payment(response, operation).state, "PROCESSING");
    assert.throws(() => payment({...response, action: action("ALIPAY")}, operation));
    assert.throws(() => payment(response, {...operation, choice: {channel: "WECHAT", method: "H5"}}));
    assert.throws(() => payment({...response, state: "SUCCEEDED"}, operation));
});
