"use strict";
import {CheckoutError, id, choice, choiceKey, order as readOrder, payment as readPayment, finalPayment,
    parsePayload, validateAmount, formatAmount, actionKey, newOperation, restoreOperation, attachPayment, persist, forget, startBody, redirect, recovery} from "/checkout-core.js";

const params = new URLSearchParams(location.hash.slice(1));
const tenant = params.get("tenant"), orderId = params.get("order"), token = params.get("token");
const node = name => document.getElementById(name);
const message = node("message"), panel = node("order"), refreshButton = node("refresh");
const form = node("payment-form"), amountInput = node("payment-amount"), choiceInput = node("payment-choice"), payButton = node("pay");
const launch = node("launch"), operationPanel = node("payment-operation");
const states = {INIT: "待付款", PENDING: "付款中", CLOSING: "关闭处理中", CLOSED: "已关闭", PAID: "已付清"};
const paymentStates = {INIT: "正在准备支付", PROCESSING: "付款处理中", UNKNOWN: "等待确认付款结果", CLOSING: "正在核对关闭结果",
    SUCCEEDED: "本次付款成功", FAILED: "本次付款未成功", CLOSED: "本次付款已关闭"};
const channelNames = {ALIPAY: "支付宝", WECHAT: "微信", UNIONPAY: "银联"};
let summary = null, operation = null, busy = false, unavailable = false, recoveryBlocked = false;
let storage, storageKey, pollTimer, pollCount = 0, expiryTimer, action = null;

function notice(text) { message.textContent = text; }
function stopPoll() { clearTimeout(pollTimer); pollTimer = null; }
function selectedName(value) { return `${channelNames[value.channel]} · 网页支付`; }
function paintOrder(value) {
    summary = readOrder(value, orderId);
    node("subject").textContent = summary.subject;
    node("order-id").textContent = orderId;
    for (const [element, field] of [["amount", "amountMinor"], ["paid", "paidAmountMinor"],
        ["held", "paymentReservedMinor"], ["available", "availableToPayMinor"]]) node(element).textContent = `¥${formatAmount(summary[field])}`;
    node("state").textContent = states[summary.state];
    node("amount-rules").textContent = `单笔 ¥${formatAmount(summary.minimumPaymentMinor)}—¥${formatAmount(summary.maximumPaymentMinor)}`
        + (summary.allowExactRemainderBelowMin ? "；准确尾款可低于最低金额。" : "。")
        + (summary.remainingPaymentCount == null ? "" : ` 还可发起 ${summary.remainingPaymentCount} 笔付款。`);
    panel.hidden = false;
    paintControls();
}
function paintControls() {
    refreshButton.disabled = busy;
    const payable = summary && ["INIT", "PENDING"].includes(summary.state) && summary.availableToPayMinor > 0n
        && summary.remainingPaymentCount !== 0n && summary.choices.length > 0;
    form.hidden = !summary || !operation && !payable;
    amountInput.readOnly = !!operation;
    amountInput.disabled = busy || unavailable || recoveryBlocked;
    choiceInput.disabled = busy || unavailable || recoveryBlocked || !!operation;
    const previous = choiceInput.value;
    choiceInput.replaceChildren();
    const supplied = operation ? [operation.choice] : summary?.choices ?? [];
    for (const item of supplied) {
        const option = document.createElement("option");
        option.value = JSON.stringify(item); option.textContent = selectedName(item); choiceInput.append(option);
    }
    if (operation) amountInput.value = formatAmount(BigInt(operation.amountMinor));
    else if ([...choiceInput.options].some(option => option.value === previous)) choiceInput.value = previous;
    payButton.disabled = busy || unavailable || recoveryBlocked || (operation ? operation.paymentId !== null : !payable);
    payButton.textContent = operation ? "按原金额重试本次付款" : "确认本次付款";
    operationPanel.hidden = !operation;
    if (operation) {
        node("payment-id").textContent = operation.paymentId ?? "等待本次请求确认";
        node("payment-value").textContent = `¥${formatAmount(BigInt(operation.amountMinor))}`;
    }
}
function paintAction(value) {
    clearTimeout(expiryTimer); action = value;
    launch.hidden = true; launch.removeAttribute("href");
    if (unavailable || value == null) return;
    const href = redirect(value);
    if (href === null) return;
    launch.href = href; launch.hidden = false;
    const delay = Math.min(2147483647, Math.max(0, Date.parse(value.expiresAt) - Date.now()));
    expiryTimer = setTimeout(() => {
        paintAction(action);
        if (launch.hidden && operation) notice("支付地址已到期，请刷新核对原付款；不要另建付款重复尝试。");
    }, delay);
}
function schedulePoll() {
    stopPoll();
    if (!operation || unavailable || recoveryBlocked || document.hidden) return;
    if (pollCount >= 30) { notice("付款结果仍待确认，请稍后手动刷新；本次操作已保留。"); return; }
    pollTimer = setTimeout(() => refresh(false), 3000);
}
function failure(error) {
    const code = error?.code;
    if (code === "ACQUIRE_SESSION_UNAUTHORIZED" || code === "ACQUIRE_SESSION_UNAVAILABLE" || code === "AUTH_UNAVAILABLE") {
        unavailable = true; stopPoll(); paintAction(null);
        notice("会话已不可用，请向商户获取新链接；已有付款请由商户核对，勿重复支付。");
    } else if (code === "STORAGE_UNAVAILABLE" || code === "RECOVERY_UNAVAILABLE") {
        recoveryBlocked = true; stopPoll();
        notice("本次操作信息无法可靠保存或读取，请保留页面并联系商户核对。");
    } else if (operation) {
        stopPoll(); notice("本次结果尚未确认，操作信息已保留。请刷新查询或按原金额重试，不要另建付款。");
    } else if (["INVALID_AMOUNT", "AMOUNT_UNAVAILABLE", "AMOUNT_BELOW_MINIMUM", "ORDER_NOT_PAYABLE"].includes(code)) {
        notice(code === "AMOUNT_BELOW_MINIMUM" ? "金额低于单笔最低金额；最后一笔请填写准确尾款。"
            : code === "INVALID_AMOUNT" ? "请输入大于 0、最多两位小数的金额。"
            : "金额或可付状态已变化，请按当前可付金额和单笔规则填写，必要时刷新状态。");
    } else notice("暂时无法确认订单状态，请刷新重试。金额与付款结果以服务端为准。");
}
async function request(path, body) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    try {
        const response = await fetch(path, {method: "POST", cache: "no-store", credentials: "omit", redirect: "error", referrerPolicy: "no-referrer",
            signal: controller.signal, headers: {"Content-Type": "application/json", "Authorization": `Bearer ${token}`,
                "X-Checkout-Tenant": tenant}, body});
        if (response.status === 401 || response.status === 403) throw new CheckoutError("AUTH_UNAVAILABLE");
        if (!response.ok) throw new CheckoutError("REQUEST_UNCERTAIN");
        const envelope = parsePayload(await response.text());
        if (!envelope.result || envelope.result.code !== "SUCCESS" || envelope.result.status !== "S" || !envelope.data)
            throw new CheckoutError(typeof envelope.result?.code === "string" ? envelope.result.code : "REQUEST_UNCERTAIN");
        return envelope.data;
    } finally { clearTimeout(timeout); }
}
function applyPayment(raw, fromProgress) {
    const result = readPayment(raw, operation, fromProgress);
    operation = attachPayment(operation, result.paymentId);
    paintOrder(result.order);
    node("payment-status").textContent = result.accepted ? paymentStates[result.state] : "本次付款未受理";
    // Starting can observe a terminal payment before acquire has applied its contribution. Refresh must converge it first.
    if (fromProgress && finalPayment(result)) {
        forget(storage, storageKey);
        operation = null; paintAction(null); stopPoll(); amountInput.value = "";
        notice(result.state == null ? "本次付款未被受理，请以当前订单状态为准。"
            : result.state === "SUCCEEDED" && summary.state === "PAID" ? "订单已付清。"
            : result.state === "SUCCEEDED" ? "本次付款成功，可按剩余金额继续付款。" : "本次付款已结束，请核对订单后再操作。");
    } else {
        persist(storage, storageKey, operation);
        paintAction(finalPayment(result) ? null : result.action);
        notice(finalPayment(result) ? "正在核对付款对订单的影响，请稍候。"
            : !launch.hidden ? "支付地址已准备好，请点击前往支付；返回后将继续核对结果。"
            : paymentStates[result.state] + "，请稍候或手动刷新。");
    }
}
async function recoverOriginal() {
    const result = recovery(await request("/api/cashier/payments/recover", startBody(operation)), operation);
    if (result.state === "RESOLVED") {
        operation = attachPayment(operation, result.payment.paymentId);
        applyPayment(result.payment, true);
        return true;
    }
    // Neither a missing local intent nor a pending input authorizes a new action key.
    paintOrder(await request("/api/cashier/order/summary", JSON.stringify({id: orderId})));
    notice(result.state === "INPUT_REQUIRED" ? "已找到原付款意图，支付资料尚待确认；请保留本次操作，可按原金额重试。"
        : "暂未观察到本次付款意图；本次操作已保留，可按原动作键和金额重试，不能据此判断未收款。");
    return false;
}
async function refresh(manual = true) {
    if (busy || unavailable) return;
    if (!manual && pollCount >= 30) return;
    if (manual) pollCount = 0; else pollCount++;
    busy = true; stopPoll(); paintControls();
    let succeeded = false;
    try {
        if (operation?.paymentId) {
            applyPayment(await request("/api/cashier/payments/progress", JSON.stringify({id: operation.paymentId})), true);
        } else if (operation) {
            await recoverOriginal();
        } else {
            paintOrder(await request("/api/cashier/order/summary", JSON.stringify({id: orderId})));
            notice(summary.state === "PAID" ? "订单已付清。" : summary.state === "CLOSED" ? "订单已关闭。"
                : summary.choices.length ? "请手动填写本次付款金额，可分次支付。" : "当前无可用付款方式或金额，请联系商户。");
        }
        succeeded = true;
    } catch (error) { failure(error); }
    finally { busy = false; paintControls(); if (succeeded) schedulePoll(); }
}
async function start(event) {
    event.preventDefault();
    if (busy || unavailable || recoveryBlocked || operation?.paymentId) return;
    busy = true; pollCount = 0; stopPoll(); paintControls();
    let succeeded = false;
    try {
        if (operation && await recoverOriginal()) { succeeded = true; return; }
        if (!operation) {
            const selected = choice(JSON.parse(choiceInput.value));
            if (!summary.choices.some(item => choiceKey(item) === choiceKey(selected))) throw new CheckoutError("UNSUPPORTED_CHOICE");
            const amount = validateAmount(amountInput.value, summary);
            const candidate = newOperation(orderId, amount, selected, actionKey());
            persist(storage, storageKey, candidate); // Durable intent metadata precedes the first HTTP call; never stores the capability.
            operation = candidate;
            pollCount = 0;
        }
        node("payment-status").textContent = "正在提交本次固定金额…";
        paintControls();
        applyPayment(await request("/api/cashier/payments/start", startBody(operation)), false);
        succeeded = true;
    } catch (error) { failure(error); }
    finally { busy = false; paintControls(); if (succeeded) schedulePoll(); }
}
launch.addEventListener("click", event => {
    try {
        if (unavailable || redirect(action) === null) {
            event.preventDefault(); paintAction(null); notice("支付地址不可用，请刷新核对原付款结果。"); return;
        }
        notice("请在支付页面确认；此处显示的结果以订单查询为准。");
    } catch (error) { event.preventDefault(); paintAction(null); failure(error); }
});
form.addEventListener("submit", start);
refreshButton.addEventListener("click", () => refresh(true));
document.addEventListener("visibilitychange", () => {
    if (document.hidden) stopPoll();
    else if (operation && !busy && pollCount < 30) refresh(false);
});
window.addEventListener("pagehide", () => { stopPoll(); clearTimeout(expiryTimer); });
window.addEventListener("pageshow", event => { if (event.persisted && operation) refresh(false); });
try {
    // Strip both fragment and query before any authenticated request. Nothing persists the bearer capability.
    history.replaceState(null, "", location.pathname);
    if (["tenant", "order", "token"].some(key => params.getAll(key).length !== 1) || !token
        || !/^[A-Za-z0-9_-]{1,64}\.[A-Za-z0-9_-]{43}$/.test(token)) throw new CheckoutError("INVALID_LINK");
    id(tenant); id(orderId);
    storageKey = "loadup.checkout.v1:" + JSON.stringify([tenant, orderId, token.split(".")[0]]);
    try {
        storage = window.sessionStorage;
        const saved = storage.getItem(storageKey);
        if (saved !== null) operation = restoreOperation(saved, orderId);
    } catch (_) { throw new CheckoutError("RECOVERY_UNAVAILABLE"); }
    refresh(true);
} catch (error) {
    if (error?.code === "RECOVERY_UNAVAILABLE") { failure(error); refresh(true); }
    else { recoveryBlocked = true; notice("收银台链接无效，请重新打开商户提供的原链接。"); }
    paintControls();
}
