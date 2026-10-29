"use strict";
import {CheckoutError, invocation} from "./checkout-core.mjs";

/** Native callbacks are interaction hints only. Every return still requires the original server-side query. */
export function createBridgePayments({
                                         window: host = globalThis,
                                         document: events = globalThis.document,
                                         now = () => Date.now(),
                                         setTimeout: later = globalThis.setTimeout,
                                         clearTimeout: cancel = globalThis.clearTimeout,
                                         readyTimeoutMs = 5000,
                                         callbackTimeoutMs = 60000
                                     } = {}) {
    if (!host || !events || typeof events.addEventListener !== "function" || typeof events.removeEventListener !== "function"
        || typeof now !== "function" || typeof later !== "function" || typeof cancel !== "function"
        || !Number.isSafeInteger(readyTimeoutMs) || readyTimeoutMs < 1 || readyTimeoutMs > 30000
        || !Number.isSafeInteger(callbackTimeoutMs) || callbackTimeoutMs < 1 || callbackTimeoutMs > 300000)
        throw new CheckoutError("BRIDGE_UNAVAILABLE");
    let active = null;

    function invoke(action, selected, {signal} = {}) {
        if (active !== null) return Promise.reject(new CheckoutError("BRIDGE_BUSY"));
        if (signal != null && (typeof signal.addEventListener !== "function" || typeof signal.removeEventListener !== "function"
            || typeof signal.aborted !== "boolean")) return Promise.reject(new CheckoutError("BRIDGE_UNAVAILABLE"));
        let snapshot;
        try {
            snapshot = invocation(action, selected, now());
        } catch (error) {
            return Promise.reject(error);
        }
        if (action?.type !== "INVOKE_JSAPI") return Promise.reject(new CheckoutError("UNSUPPORTED_CHOICE"));
        if (snapshot === null) return Promise.resolve(Object.freeze({status: "EXPIRED", invoked: false}));
        if (signal?.aborted) return Promise.resolve(Object.freeze({status: "ABORTED", invoked: false}));

        return new Promise(resolve => {
            let settled = false, invoked = false, timer;
            const token = {};
            active = token;
            const readyEvent = snapshot.channel === "WECHAT" ? "WeixinJSBridgeReady" : "AlipayJSBridgeReady";
            const finish = status => {
                if (settled) return;
                settled = true;
                cancel(timer);
                events.removeEventListener(readyEvent, tryLaunch);
                signal?.removeEventListener("abort", abort);
                if (active === token) active = null;
                resolve(Object.freeze({status, invoked}));
            };
            const abort = () => finish(invoked ? "UNKNOWN" : "ABORTED");
            const callback = value => {
                // Never consume callbackUrl, memo, signatures, or browser claims of financial success.
                const cancelled = snapshot.channel === "WECHAT"
                    ? value?.err_msg === "get_brand_wcpay_request:cancel"
                    : value?.resultCode === "6001" || value?.resultCode === 6001;
                finish(cancelled ? "CANCELLED" : "RETURNED");
            };

            function tryLaunch() {
                if (settled || invoked) return;
                if (signal?.aborted) {
                    abort();
                    return;
                }
                if (now() >= snapshot.expiresAt) {
                    finish("EXPIRED");
                    return;
                }
                const bridge = snapshot.channel === "WECHAT" ? host.WeixinJSBridge : host.AlipayJSBridge;
                const method = snapshot.channel === "WECHAT" ? "invoke" : "call";
                if (!bridge || typeof bridge[method] !== "function") return;
                invoked = true;
                cancel(timer);
                events.removeEventListener(readyEvent, tryLaunch);
                timer = later(() => finish("UNKNOWN"), callbackTimeoutMs);
                try {
                    bridge[method](snapshot.channel === "WECHAT" ? "getBrandWCPayRequest" : "tradePay", {...snapshot.parameters}, callback);
                } catch (_) {
                    // The native call might already have reached the wallet. Do not classify it as no collection.
                    finish("UNKNOWN");
                }
            }

            try {
                events.addEventListener(readyEvent, tryLaunch);
                signal?.addEventListener("abort", abort, {once: true});
                timer = later(() => finish(now() >= snapshot.expiresAt ? "EXPIRED" : "UNAVAILABLE"),
                    Math.min(readyTimeoutMs, Math.max(1, snapshot.expiresAt - now())));
                tryLaunch();
            } catch (_) {
                finish(invoked ? "UNKNOWN" : "UNAVAILABLE");
            }
        });
    }

    return Object.freeze({invoke});
}
