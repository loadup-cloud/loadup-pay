import test from "node:test";
import assert from "node:assert/strict";
import {MAX_MINOR, parseAmount, formatAmount, parsePayload, order, validateAmount, actionKey, newOperation, restoreOperation,
    attachPayment, persist, forget, startBody, payment, finalPayment, redirect, recovery} from "../../main/resources/cashier/checkout-core.mjs";

const selected = {channel: "WECHAT", method: "H5"};
function summary(overrides = {}) {
    return {orderId: "order", subject: "<script>text only</script>", currency: "CNY", state: "PENDING", amountMinor: 10000n,
        paidAmountMinor: 2000n, paymentReservedMinor: 0n, availableToPayMinor: 8000n,
        minimumPaymentMinor: 100n, maximumPaymentMinor: 10000n, allowExactRemainderBelowMin: true,
        remainingPaymentCount: 3n, choices: [selected], ...overrides};
}
function operation(amount = 3000n) { return newOperation("order", amount, selected, "fixed-action"); }
function memory() {
    const data = new Map();
    return {getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key)};
}

test("action keys use a cryptographic source and never fall back to an untrusted random generator", () => {
    const source = {getRandomValues(bytes) { for (let i = 0; i < bytes.length; i++) bytes[i] = i; return bytes; }};
    assert.equal(actionKey(source), "pay_000102030405060708090a0b0c0d0e0f");
    assert.throws(() => actionKey(null));
});

test("decimal input and display preserve every minor unit through the signed int64 limit", () => {
    assert.equal(parseAmount("0.01"), 1n);
    assert.equal(parseAmount("30.5"), 3050n);
    assert.equal(parseAmount("92233720368547758.07"), MAX_MINOR);
    assert.equal(formatAmount(MAX_MINOR), "92233720368547758.07");
    for (const invalid of ["0", "-1", "+1", "01", "1e2", ".01", "1.", "1.001", "92233720368547758.08", "NaN", "Infinity"])
        assert.throws(() => parseAmount(invalid));
});

test("JSON decoding never silently rounds monetary integers or versions", () => {
    assert.equal(parsePayload('{"amountMinor":"9007199254740993"}').amountMinor, 9007199254740993n);
    assert.equal(parsePayload('{"version":null}').version, null);
    const hasSource = JSON.parse("0", (_key, _value, context) => context?.source) === "0";
    const numeric = '{"amountMinor":9007199254740993,"version":9007199254740995}';
    if (hasSource) {
        const restored = parsePayload(numeric);
        assert.equal(restored.amountMinor, 9007199254740993n);
        assert.equal(restored.version, 9007199254740995n);
    } else assert.throws(() => parsePayload(numeric));
    assert.throws(() => parsePayload('{"amountMinor":"001"}'));
    assert.throws(() => parsePayload('{"amountMinor":"9223372036854775808"}'));
});

test("manual partial payments and exact remainder use frozen limits without floating point", () => {
    const current = order(summary(), "order");
    assert.equal(validateAmount("30.00", current), 3000n);
    const remainder = order(summary({availableToPayMinor: 50n}), "order");
    assert.equal(validateAmount("0.50", remainder), 50n);
    assert.throws(() => validateAmount("0.49", remainder));
    assert.throws(() => validateAmount("0.50", {...remainder, allowExactRemainderBelowMin: false}));
    assert.throws(() => validateAmount("80.01", current));
    assert.throws(() => validateAmount("30", {...current, remainingPaymentCount: 0n}));
    assert.throws(() => validateAmount("30", {...current, state: "CLOSED"}));
});

test("order scope, gross totals, reservations and terminal balances must remain coherent", () => {
    assert.throws(() => order(summary(), "another-order"));
    assert.throws(() => order(summary({paymentReservedMinor: 9000n}), "order"));
    assert.throws(() => order(summary({availableToPayMinor: 8001n}), "order"));
    assert.throws(() => order(summary({state: "PAID"}), "order"));
    assert.throws(() => order(summary({currency: "USD"}), "order"));
    assert.equal(order(summary({state: "PAID", paidAmountMinor: 10000n, availableToPayMinor: 0n, choices: []}), "order").state, "PAID");
});

test("recovery preserves original action, amount and payment id while excluding capabilities and redirect payloads", () => {
    const storage = memory(), original = attachPayment(operation(), "payment");
    persist(storage, "context", {...original, token: "must-not-persist", action: {redirectUrl: "must-not-persist"}});
    const raw = storage.getItem("context");
    assert.equal(raw.includes("must-not-persist"), false);
    const restored = restoreOperation(raw, "order");
    assert.deepEqual(restored, original);
    assert.equal(startBody(restored), startBody(original));
    assert.throws(() => restoreOperation(raw, "another-order"));
    assert.throws(() => attachPayment(restored, "another-payment"));
    assert.throws(() => restoreOperation("broken-record", "order"));
});

test("start body carries the exact int64 numeric literal and a stable action key", () => {
    const original = operation(9007199254740993n);
    const body = startBody(original);
    assert.equal(body.includes('"amountMinor":9007199254740993'), true);
    assert.equal(body.includes('"actionKey":"fixed-action"'), true);
    assert.equal(startBody(restoreOperation(JSON.stringify(original), "order")), body);
});

test("metadata storage must actually retain writes and confirm removals", () => {
    const rejected = {setItem() {}, getItem() {return null;}};
    assert.throws(() => persist(rejected, "context", operation()));
    const stuck = {removeItem() {}, getItem() {return "still-present";}};
    assert.throws(() => forget(stuck, "context"));
    const storage = memory();
    persist(storage, "context", operation()); forget(storage, "context");
    assert.equal(storage.getItem("context"), null);
});

test("acceptedness and identity must be explicit before any operation may finish", () => {
    const pending = attachPayment(operation(), "payment");
    const base = {orderId: "order", paymentId: "payment", accepted: true, state: "PROCESSING", version: 2n, action: null, order: summary()};
    assert.equal(finalPayment(payment(base, pending, true)), false);
    assert.throws(() => payment({...base, paymentId: "other"}, pending, true));
    assert.throws(() => payment({...base, accepted: undefined, state: null, version: null}, pending, true));
    assert.throws(() => payment({...base, state: null, version: null}, pending, true));
    const absent = {...base, accepted: false, state: null, version: null};
    assert.equal(finalPayment(payment(absent, pending, true)), true);
    assert.throws(() => payment(absent, pending, false));
    assert.throws(() => payment(absent, operation(), true));
    assert.throws(() => payment({...base, state: "SUCCEEDED", action: {type: "REDIRECT"}}, pending, true));
});

test("launch requires the original unexpired HTTPS redirect and preserves raw encoding", () => {
    const action = {type: "REDIRECT", expiresAt: "2026-10-24T12:00:00+08:00", data: {type: "REDIRECT", redirectUrl: "https://pay.example.test/launch?q=%2F&a=1"}};
    const before = Date.parse("2026-10-24T11:59:59+08:00"), deadline = Date.parse(action.expiresAt);
    assert.equal(redirect(action, before), action.data.redirectUrl);
    assert.equal(redirect(action, deadline), null);
    for (const invalid of ["javascript:alert(1)", "http://pay.example.test/launch", "https://name:secret@pay.example.test/launch",
        "https://pay.example.test/launch#unexpected", " https://pay.example.test/launch", "https://pay.example.test/\nlaunch"])
        assert.throws(() => redirect({...action, data: {...action.data, redirectUrl: invalid}}, before));
    assert.equal(redirect({type: "WAIT"}, before), null);
});

test("lost-response recovery binds the exact action and never turns lookup absence into non-payment", () => {
    const original = operation();
    const base = {orderId: "order", actionKey: original.actionKey, state: "NOT_OBSERVED", payment: null};
    assert.equal(recovery(base, original).payment, null);
    assert.equal(recovery({...base, state: "INPUT_REQUIRED"}, original).state, "INPUT_REQUIRED");
    assert.throws(() => recovery({...base, actionKey: "other-action"}, original));
    assert.throws(() => recovery({...base, orderId: "other-order"}, original));
    assert.throws(() => recovery({...base, state: "FAILED"}, original));
    assert.throws(() => recovery({...base, payment: {}}, original));
    const nested = {orderId: "order", paymentId: "payment", accepted: true, state: "PROCESSING", version: 1n, action: null, order: summary()};
    const resolved = {...base, state: "RESOLVED", payment: nested};
    assert.equal(finalPayment(recovery(resolved, original).payment), false);
    assert.equal(recovery(resolved, original).payment.paymentId, "payment");
    assert.throws(() => recovery({...resolved, payment: {...nested, paymentId: "other"}}, attachPayment(original, "payment")));
    const absent = {...nested, accepted: false, state: null, version: null};
    assert.equal(finalPayment(recovery({...resolved, payment: absent}, original).payment), true);
    assert.throws(() => recovery({...resolved, payment: {...absent, accepted: undefined}}, original));
    assert.throws(() => recovery(base, attachPayment(original, "payment")));
});
