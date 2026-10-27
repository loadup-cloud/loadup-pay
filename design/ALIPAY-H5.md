# 支付宝手机 H5 接入

更新：2026-10-27。本页记录当前源码契约；未构建、未运行测试、未迁移或联调渠道。

## 官方依据与模式

用户提供的 [官方索引](https://opendocs.alipay.com/open/llms.txt) 指向
[手机网站快速接入](https://opendocs.alipay.com/open/00f7nh.md)、
[手机网站 API 2.0](https://opendocs.alipay.com/open/02ivbs.md) 和
[接入准备](https://opendocs.alipay.com/open/00f7ng.md)。手机 H5 对应 `alipay.trade.wap.pay` 与
`QUICK_WAP_WAY`，不是电脑网站 `alipay.trade.page.pay`。官方当前文档明确列出第三方应用代调用，
其公共参数包含 `app_auth_token`；不能再以“官方未支持服务商 H5”描述缺口。

官方页面型 API 在服务端生成表单或 GET 跳转请求，由买家浏览器提交；生成动作没有验签渠道响应，
也不证明订单已在渠道创建、渠道受理或付款成功。前台回跳不作为资金证据。

当前实现直连 H5。服务商 H5 仍未实现：标准页面请求会把授权参数交给浏览器，与项目现有“不暴露长期
商户授权令牌”约束冲突。不能偷偷删掉令牌改用服务商自有收款账户，也不能把 PC 网站文档当作另一种
无令牌 WAP 协议。后续需确定满足该约束的官方承载方式；这是产品协议与凭证交付问题，不能用假成功绕过。

## 请求与路由

上游继续使用现有 `choice={channel:ALIPAY,method:H5}`，可走托管收银台或商户纯 API。
Facade 按渠道注册策略路由，支付宝协议只在 channel-alipay。payment 不拼支付宝请求，也不引入支付宝 SDK。

`AlipaySubmitAdapter` 在 Y2 前检查原绑定具备 H5、模式为 DIRECT、无 operatorAppId、原时间窗与通知地址。
金额为本次固定付款份额，最大 100000000 元；使用整数分转两位元字符串。原商户请求号、标题、
向下取整到秒的绝对截止时间沿用付款意图。H5 不强制要求买家主档、OAuth 标识或扫码输入，已有 H5 场景输入可沿原安全输入流程传递。

`AlipayProtocolClient.directWapUrl` 读取冻结版本凭证并使用现有 KmsTemplate / RSA2 生成签名，
不调用 HTTP、不验证虚构响应。DIRECT 凭证意外携带授权令牌时拒绝；协议 signer 也拒绝带令牌的 WAP 签名。
服务商模式在预检及协议客户端处拒绝，不读取或解密商户授权。服务端 PAY/CREATE/QUERY/退款仍沿原服务商路径。

## 配置与动作

H5 默认不可用，需在可信运行配置显式设置：

```yaml
loadup:
  pay:
    channel:
      alipay:
        h5:
          gateway: https://openapi.alipay.com/gateway.do
          return-urls:
            tenant-example:
              binding-revision-example: https://checkout.example.test/payment-return
        notifications:
          notify-urls:
            tenant-example:
              binding-revision-example: https://callbacks.example.test/api/payment/channel-callbacks/alipay-example
```

这只是示例，不创建端点、绑定、Secret 或部署。`return-urls` 可不配；配置需按原租户和不可变绑定 revision 提供，
回跳页自行查询原付款。notify_url 必须配置且不超过渠道 256 字节 ASCII URL 上限。
跳转与服务端查询必须使用相同渠道环境和同一组环境凭证；不自动从浏览器地址或商户参数推导网关。

支持生产网关，以及[官方沙箱说明](https://opendocs.alipay.com/open/00f7np.md)指定的
`https://openapi-sandbox.dl.alipaydev.com/gateway.do`。`AlipayH5ChannelAction` 限定 HTTPS、精确主机/路径、
无用户信息/片段/显式端口、唯一且允许的公共参数、WAP 方法和 RSA2；拒绝 app_auth_token 及其编码键。
动作整体按 DiagnosticHidden 隐去，持久 JSON 仅用于原动作恢复；不拼接商户任意回跳 URL，不向页面返回可执行 HTML。

## 本地动作与资金事实分离

`ChannelPaymentSubmission` 的本地 H5 准备结果有 action、无 observation；其他现有支付动作仍需渠道观察。
PaymentResultService 只在原付款锁和 Y2 身份/epoch 下记录动作，没有渠道回执、资金认领、退款容量或成功事件。
返回的内部 PaymentResultApplication.receiptId 为 null，不可用于渠道通知 ACK。

`payment_action.source` 显式区分 LOCAL_SIGNED / CHANNEL_RECEIPT。LOCAL_SIGNED 没有 receipt_id，
source_digest 等于原动作 payload_hash；原动作与身份、金额、epoch、原期限绑定。数据库 CHECK 和领域构造约束同步修改，
不保留旧格式兼容。这里只更新迁移源码，没有修改开发库。

现有类型化动作 codec、MapStruct 和 RPC 的 REDIRECT DTO 复用，商户无需新接口。原 active attempt、门禁 OPEN、
付款 PROCESSING 或 UNKNOWN 且动作未过期时，查询返回持久化的原地址；UNKNOWN 不会清除有效动作。
关闭中、终态、门禁封禁或过期不再提供动作。重复动作必须完全一致，不能更换签名时间或延长有效期。

后台只查原号，不重新调用 WAP 或重签地址；初次本地动作提交丢失时不能把查询 NOT_FOUND 当作重新生成提交权。
支付宝可能在用户实际支付时才创建订单，因此未支付查询不存在仍保留 UNKNOWN/占用。截止时间或未访问页面
也不等于未收款证明，沿原查证与差错处理规则收敛。

## 测试源码与待验收

已补：本地准备不调用 HTTP、固定请求参数、服务商拒绝和令牌拦截、动作编解码/篡改/重复键、
无资金回执的本地事务、UNKNOWN 原动作呈现。所有测试仅为源码，未运行。

仍需后续验收：生成链接与渠道真实 GET 流程、浏览器唤起/返回、通知/查询一致性、沙箱/生产隔离、
实际 MySQL 动作落库和回滚、并发关闭及通知先到竞态。服务商 H5 与丢失动作安全恢复仍是未完成项。
