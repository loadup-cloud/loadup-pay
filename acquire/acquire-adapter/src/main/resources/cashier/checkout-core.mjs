"use strict";

export const MAX_MINOR = 9223372036854775807n;
const integerFields = new Set(["amountMinor", "paidAmountMinor", "paymentReservedMinor", "availableToPayMinor",
    "minimumPaymentMinor", "maximumPaymentMinor", "remainingPaymentCount", "version"]);
const orderStates = new Set(["INIT", "PENDING", "CLOSING", "CLOSED", "PAID"]);
const paymentStates = new Set(["INIT", "PROCESSING", "UNKNOWN", "CLOSING", "SUCCEEDED", "FAILED", "CLOSED"]);
const finalStates = new Set(["SUCCEEDED", "FAILED", "CLOSED"]);
const channels = new Set(["ALIPAY", "WECHAT", "UNIONPAY"]);

export class CheckoutError extends Error {
    constructor(code) {
        super(code);
        this.code = code;
    }
}

function require(value, code = "INVALID_RESPONSE") {
    if (!value) throw new CheckoutError(code);
}

function object(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function id(value, maximum = 64) {
    require(typeof value === "string" && value.length > 0 && value.length <= maximum
        && value.trim() === value && !/[\u0000-\u001f\u007f]/.test(value), "INVALID_ID");
    return value;
}

export function minor(text) {
    require(typeof text === "string" && /^(0|[1-9]\d{0,18})$/.test(text), "INVALID_AMOUNT");
    const value = BigInt(text);
    require(value <= MAX_MINOR, "INVALID_AMOUNT");
    return value;
}

function money(value) {
    require(typeof value === "bigint" && value >= 0n && value <= MAX_MINOR, "INVALID_AMOUNT");
    return value;
}

export function parseAmount(text) {
    require(typeof text === "string" && /^(0|[1-9]\d{0,16})(\.\d{1,2})?$/.test(text.trim()), "INVALID_AMOUNT");
    const [major, fraction = ""] = text.trim().split(".");
    const value = BigInt(major) * 100n + BigInt(fraction.padEnd(2, "0"));
    require(value > 0n && value <= MAX_MINOR, "INVALID_AMOUNT");
    return value;
}

export function formatAmount(value) {
    money(value);
    return `${value / 100n}.${String(value % 100n).padStart(2, "0")}`;
}

export function parsePayload(text) {
    require(typeof text === "string" && text.length <= 65536);
    return JSON.parse(text, (key, value, context) => {
        if (!integerFields.has(key)) return value;
        if (value == null && (key === "version" || key === "remainingPaymentCount")) return null;
        if (typeof value === "string") return minor(value);
        if (context && typeof context.source === "string") return minor(context.source);
        require(Number.isSafeInteger(value) && value >= 0, "UNSAFE_INTEGER");
        return BigInt(value);
    });
}

export function choice(value) {
    require(object(value) && channels.has(value.channel) && value.method === "H5", "UNSUPPORTED_CHOICE");
    return Object.freeze({channel: value.channel, method: value.method});
}

export function choiceKey(value) {
    const checked = choice(value);
    return `${checked.channel}/${checked.method}`;
}

export function order(value, orderId) {
    require(object(value) && value.orderId === id(orderId) && value.currency === "CNY" && orderStates.has(value.state));
    require(typeof value.subject === "string" && value.subject.length <= 200);
    const total = money(value.amountMinor), paid = money(value.paidAmountMinor),
        held = money(value.paymentReservedMinor);
    const available = money(value.availableToPayMinor);
    const minimum = money(value.minimumPaymentMinor), maximum = money(value.maximumPaymentMinor);
    require(total > 0n && paid <= total && held <= total - paid && available <= total - paid - held);
    require(minimum > 0n && maximum >= minimum && typeof value.allowExactRemainderBelowMin === "boolean");
    const slots = value.remainingPaymentCount ?? null;
    require(slots === null || typeof slots === "bigint" && slots >= 0n && slots <= 2147483647n);
    if (value.state === "PAID") require(paid === total && held === 0n && available === 0n);
    if (value.state === "CLOSING" || value.state === "CLOSED") require(available === 0n);
    const supplied = value.choices ?? [];
    require(Array.isArray(supplied) && supplied.length <= 16);
    const allowed = [], seen = new Set();
    for (const item of supplied) {
        const normalized = choice(item), key = choiceKey(normalized);
        if (!seen.has(key)) {
            seen.add(key);
            allowed.push(normalized);
        }
    }
    return Object.freeze({...value, remainingPaymentCount: slots, choices: Object.freeze(allowed)});
}

export function validateAmount(text, current) {
    const value = parseAmount(text);
    require(current.state === "INIT" || current.state === "PENDING", "ORDER_NOT_PAYABLE");
    require(current.remainingPaymentCount !== 0n && value <= current.availableToPayMinor && value <= current.maximumPaymentMinor, "AMOUNT_UNAVAILABLE");
    require(value >= current.minimumPaymentMinor || current.allowExactRemainderBelowMin && value === current.availableToPayMinor, "AMOUNT_BELOW_MINIMUM");
    return value;
}

export function actionKey(cryptoSource = globalThis.crypto) {
    require(cryptoSource && typeof cryptoSource.getRandomValues === "function", "CRYPTO_UNAVAILABLE");
    const bytes = new Uint8Array(16);
    cryptoSource.getRandomValues(bytes);
    return "pay_" + [...bytes].map(value => value.toString(16).padStart(2, "0")).join("");
}

export function newOperation(orderId, amountMinor, selected, actionKey) {
    money(amountMinor);
    require(amountMinor > 0n, "INVALID_AMOUNT");
    return Object.freeze({
        schemaVersion: 1, orderId: id(orderId), actionKey: id(actionKey, 128),
        amountMinor: String(amountMinor), choice: choice(selected), paymentId: null
    });
}

export function restoreOperation(text, orderId) {
    require(typeof text === "string" && text.length <= 2048, "RECOVERY_UNAVAILABLE");
    const value = JSON.parse(text);
    require(object(value) && value.schemaVersion === 1 && value.orderId === orderId, "RECOVERY_UNAVAILABLE");
    const restored = newOperation(value.orderId, minor(value.amountMinor), value.choice, value.actionKey);
    return Object.freeze({...restored, paymentId: value.paymentId == null ? null : id(value.paymentId)});
}

export function attachPayment(operation, paymentId) {
    id(paymentId);
    require(operation.paymentId === null || operation.paymentId === paymentId);
    return Object.freeze({...operation, paymentId});
}

export function persist(storage, key, operation) {
    const text = JSON.stringify(restoreOperation(JSON.stringify(operation), operation.orderId));
    try {
        storage.setItem(key, text);
        require(storage.getItem(key) === text, "STORAGE_UNAVAILABLE");
    } catch (_) {
        throw new CheckoutError("STORAGE_UNAVAILABLE");
    }
}

export function forget(storage, key) {
    try {
        storage.removeItem(key);
        require(storage.getItem(key) === null, "STORAGE_UNAVAILABLE");
    } catch (_) {
        throw new CheckoutError("STORAGE_UNAVAILABLE");
    }
}

export function startBody(operation) {
    const checked = restoreOperation(JSON.stringify(operation), operation.orderId);
    const base = JSON.stringify({orderId: checked.orderId, actionKey: checked.actionKey, choice: checked.choice});
    // Insert only a validated integer literal; JSON.stringify cannot encode BigInt and Number loses int64 precision.
    return base.slice(0, -1) + ',"amountMinor":' + checked.amountMinor + '}';
}

export function payment(value, operation, allowNotAccepted = false) {
    require(object(value) && value.orderId === operation.orderId);
    id(value.paymentId);
    require(operation.paymentId === null || operation.paymentId === value.paymentId);
    const summary = order(value.order, operation.orderId);
    require(typeof value.accepted === "boolean");
    if (!value.accepted) {
        require(allowNotAccepted && operation.paymentId !== null && value.state == null && value.version == null && value.action == null);
    } else {
        require(paymentStates.has(value.state) && typeof value.version === "bigint" && value.version >= 0n);
        if (finalStates.has(value.state) || value.state === "INIT") require(value.action == null);
    }
    if (value.action != null) require(object(value.action) && ["REDIRECT", "WAIT", "NONE"].includes(value.action.type));
    return Object.freeze({...value, order: summary});
}

export function finalPayment(value) {
    return value.accepted === false || finalStates.has(value.state);
}

export function recovery(value, operation) {
    require(object(value) && value.orderId === operation.orderId && value.actionKey === operation.actionKey);
    require(["NOT_OBSERVED", "INPUT_REQUIRED", "RESOLVED"].includes(value.state));
    if (value.state !== "RESOLVED") {
        require(value.payment == null && operation.paymentId === null);
        return Object.freeze({...value, payment: null});
    }
    require(object(value.payment));
    const bound = attachPayment(operation, id(value.payment.paymentId));
    // RESOLVED identifies the original intent; only the nested converged payment carries financial facts.
    return Object.freeze({...value, payment: payment(value.payment, bound, true)});
}

export function redirect(action, now = Date.now()) {
    if (action == null || action.type !== "REDIRECT") return null;
    require(object(action.data) && action.data.type === "REDIRECT");
    require(typeof action.expiresAt === "string" && action.expiresAt.length <= 64 && /(?:Z|[+-]\d{2}:\d{2})$/.test(action.expiresAt));
    const expires = Date.parse(action.expiresAt);
    require(Number.isFinite(expires));
    if (expires <= now) return null;
    const target = action.data.redirectUrl;
    require(typeof target === "string" && target.length <= 8192 && target.trim() === target && !/[\u0000-\u0020\u007f]/.test(target));
    const url = new URL(target);
    require(url.protocol === "https:" && url.hostname.length > 0 && !url.username && !url.password && !url.hash);
    return target; // Preserve the verified channel URL exactly, including its original query encoding.
}
