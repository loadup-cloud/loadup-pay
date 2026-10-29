# 收银台 JSAPI 浏览器桥接

状态：2026-10-28。已补浏览器模块、页面调用、静态资源和测试源码，未运行构建、Node 测试、IDEA、浏览器或实际支付。托管后端目前仍只开放
H5；本批没有把未完成的买家授权入口开放给商户。

## 契约与模块

`checkout-core.mjs` 负责服务端动作的严格表示检查。`checkout-bridge.mjs` 暴露
`createBridgePayments().invoke(action, choice, {signal})`，只处理已由业务后端生成的 PaymentActionDTO，不请求内部 API、交换
OAuth code、构造签名或持有商户/服务商凭证。

选择范围为支付宝/微信 JSAPI。服务器动作必须为 INVOKE_JSAPI，并与原 operation 的 channel/method 一致。微信参数使用既有
WechatJsapiActionDTO 的 appId/timeStamp/nonceStr/package/signType/paySign；支付宝使用既有 AlipayJsapiActionDTO 的
tradeNo，由模块映射为原生参数 tradeNO。严格字段集合拒绝额外授权 token 或任意 map，时间戳保持字符串，签名内容不重编码或数值化。

模块依据：[微信原生桥调用说明](https://pay.wechatpay.cn/doc/v2/merchant/4011935213)、[支付宝官方 tradePay 说明](https://global-agna.alipay.com/docs/ac/solution_api/trade_pay)
。本项目的微信签名契约沿既有 APIv3 后端 RSA 参数，不接受该历史微信示例中的 MD5/HMAC 参数，也不在浏览器重新签名。

原生桥使用固定 `getBrandWCPayRequest` / `tradePay` 方法。未注入时仅等待对应 Ready 事件，不动态加载脚本、不执行回包中的函数名或跳转地址。监听和等待有界：默认就绪
5 秒、原生回调 60 秒，可在构造模块时在明确上限内调整。

## 浏览器交互与资金事实

| 模块返回    | 含义                                             | 页面后续                         |
|-------------|--------------------------------------------------|----------------------------------|
| RETURNED    | 原生调用返回了回调                               | 查询原付款                       |
| CANCELLED   | 收到客户端取消提示                               | 仍查询原付款                     |
| UNKNOWN     | 原生入口可能已执行，但异常/超时/中断后结果未确认 | 保留原操作并查询                 |
| UNAVAILABLE | 等待期间未得到可用原生桥                         | 保留原操作，提示客户端环境并查询 |
| EXPIRED     | 开始或等待就绪时原动作已到期                     | 不唤起，查询原付款               |
| ABORTED     | 原生调用之前本地等待被撤销                       | 不唤起，保留原操作               |

invoked 只表示本地是否进入过原生方法，不是渠道受理或收款证据。模块不返回金融 SUCCEEDED/FAILED，不使用
callbackUrl/memo/result 作为业务数据；原生成功也只得到 RETURNED。

每个模块实例只允许一项在途调用。重复 Ready 事件不会重复唤起；参数在等待前冻结拷贝，随后外部对象变化不能改写原调用。未收到回调后的迟到回调不覆盖已返回
UNKNOWN，也不改页面金额或释放预占。

AbortSignal 只清理本地等待，不撤销原生支付；若已经进入钱包调用，返回 UNKNOWN。超时/动作到期也不能推断付款失败。只有服务端支付进度和
A3 收敛才决定原操作何时完成。

## 托管页面接线

页面新增独立“唤起客户端支付”按钮，只有用户点击才执行桥接；页面载入、轮询和 Ready 事件本身不创建付款。Ready 事件只续办已由用户点击启动的等待。

原 action 变化、过期或页面退出时清理等待。轮询返回同一不可变 action 不打断现有原生交互。钱包返回后刷新原 paymentId；原
actionKey、金额及本标签页的 sessionStorage 恢复规则保持。签名、prepay package、tradeNo、原生回调和 checkout capability 不进入持久
operation 元数据或日志。

静态资源由同源 GET `/checkout-core.mjs`、`/checkout-bridge.mjs` 暴露，原 `/checkout.js` 引用同步。开发期直接替换旧 core.js
资源路径，不保留兼容别名。继续 no-store/nosniff 和既有 CSP，不增加外部 CDN、CORS 或商户 API 权限。

## 纯 API 商户接入

商户可以将 checkout-core.mjs 与 checkout-bridge.mjs 同目录部署在自身网站，从自己后端得到原 PaymentActionDTO 与已选
choice，再由点击事件调用模块。模块不代替商户 API 鉴权，也不把服务端 access token 放到浏览器。返回后，商户仍须由自己的后端查询原付款。

```javascript
import {createBridgePayments} from "./checkout-bridge.mjs";
const payments = createBridgePayments();
payButton.addEventListener("click", async () => {
    const hint = await payments.invoke(serverAction, originalChoice);
    await refreshOriginalPayment();
});
```

上述变量由商户自己的页面/后端绑定。没有提供直接从其他域名 import 的开放 CORS 服务，也没有把模块称为完整收单 SDK；HTTP
鉴权、OAuth 和订单生命周期仍属业务 API。

## 后端授权仍待接通

ConfiguredCashierCapabilities 与 RequestCashierInteractionProvider 继续只提供 H5。不能仅打开前端 JSAPI 就将浏览器上送的
buyerId/openId 当成可信授权。

托管 JSAPI 下一步需要在付款前确定并冻结正确的渠道应用/原绑定、受信 redirect 目录、一次性 state 与会话/订单/渠道作用域、code
服务端交换、短期加密身份保存和服务商 app/openId 作用域；之后才可开放相应 choices。平台仍不创建消费者用户主档。

本批补的是已有纯 API 动作的客户端执行与托管页面消费能力，托管 OAuth 端到端链路、微信授权目录/支付宝应用配置、CSP/原生注入兼容和真实客户端验收仍未完成。

## 测试源码

checkout-bridge.test.mjs 覆盖两渠道精确参数、回调不作为资金结果、等待期间不可变快照、重复事件只调用一次、就绪缺失/过期/迟到回调、调用前后
Abort、单一在途/显式原动作重试、原生异常不泄露，以及错误渠道/方法/额外凭证/签名字段拒绝。另覆盖 JSAPI 动作与原 operation
绑定、终态不得携带动作、敏感动作不进入持久元数据。

没有运行测试或真实浏览器/渠道，也未以测试源码存在作为验收通过证据。
