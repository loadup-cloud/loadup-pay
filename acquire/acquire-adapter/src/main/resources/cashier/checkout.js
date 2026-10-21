"use strict";
(() => {
  const params = new URLSearchParams(location.hash.slice(1));
  history.replaceState(null, "", location.pathname); // Remove the capability before any request or navigation.
  const tenant = params.get("tenant"), orderId = params.get("order"), token = params.get("token");
  params.delete("token");
  const message = document.getElementById("message"), refresh = document.getElementById("refresh");
  const panel = document.getElementById("order");
  const amounts = new Set(["amountMinor", "paidAmountMinor", "paymentReservedMinor", "availableToPayMinor"]);
  const states = {INIT: "待付款", PENDING: "付款中", CLOSING: "关闭处理中", CLOSED: "已关闭", PAID: "已付清"};
  function amount(value) {
    if (typeof value !== "bigint" || value < 0n) throw new Error("invalid amount");
    return `¥${value / 100n}.${String(value % 100n).padStart(2, "0")}`;
  }
  function parse(text) {
    return JSON.parse(text, (key, value, context) => {
      if (!amounts.has(key)) return value;
      if (typeof value === "string" && /^\d+$/.test(value)) return BigInt(value);
      if (context && /^\d+$/.test(context.source)) return BigInt(context.source);
      if (Number.isSafeInteger(value)) return BigInt(value);
      throw new Error("unsupported integer precision");
    });
  }
  async function load() {
    refresh.disabled = true;
    try {
      const response = await fetch("/api/cashier/order/summary", {
        method: "POST", cache: "no-store", credentials: "omit", referrerPolicy: "no-referrer",
        headers: {"Content-Type": "application/json", "Authorization": `Bearer ${token}`, "X-Checkout-Tenant": tenant},
        body: JSON.stringify({id: orderId})
      });
      if (!response.ok) throw new Error("request failed");
      const body = parse(await response.text());
      if (!body.result || body.result.code !== "SUCCESS" || body.result.status !== "S" || !body.data) throw new Error("session unavailable");
      const order = body.data;
      if (order.orderId !== orderId || order.currency !== "CNY" || !states[order.state]) throw new Error("invalid order");
      document.getElementById("subject").textContent = order.subject;
      document.getElementById("order-id").textContent = order.orderId;
      document.getElementById("amount").textContent = amount(order.amountMinor);
      document.getElementById("paid").textContent = amount(order.paidAmountMinor);
      document.getElementById("held").textContent = amount(order.paymentReservedMinor);
      document.getElementById("available").textContent = amount(order.availableToPayMinor);
      document.getElementById("state").textContent = states[order.state];
      message.textContent = "订单状态以此处查询结果为准。";
      panel.hidden = false;
    } catch (_) {
      panel.hidden = true;
      message.textContent = "暂时无法读取订单，请刷新重试；若会话已过期，请向商户获取新链接。";
    } finally { refresh.disabled = false; }
  }
  refresh.addEventListener("click", load);
  if (!tenant || !orderId || !token || !/^[A-Za-z0-9_-]{1,64}\.[A-Za-z0-9_-]{43}$/.test(token)) {
    message.textContent = "收银台链接无效，请向商户获取新链接。";
    return;
  }
  load();
})();
