# 支付宝交易日账单来源

状态：2026-10-28。当前完成账单下载地址申请、RSA2 原始响应验签和私有申请凭证源码；尚未实现文件下载、ZIP/CSV 解析或注册支付宝
ChannelStatementProvider。没有执行编译、测试或真实渠道调用。

## 产品与账户边界

使用 OpenAPI v2 `alipay.data.dataservice.bill.downloadurl.query`，固定 `bill_type=trade` 和日格式 `bill_date=yyyy-MM-dd`
。不请求余额账单 signcustomer，不推断 trade_zft_merchant 或 smid 产品。直连使用原 APPID 所属商户，服务商使用原绑定授权的商户；凭证及授权
reference/version 均沿用原 revision，不能用当前路由替换。

申请只能在本地事务外执行，并核对可信租户。AlipayProtocolClient 使用已有 HttpTemplate 命名客户端 `alipay` 的
`bill-download-url` POST operation、KMS 确定版本签名、原平台公钥验签。服务商 app_auth_token 进入后端签名表单，不进入浏览器、业务
DTO 或私有凭证正文。

依据：[官方账单 API](https://opendocs.alipay.com/open/02ekfu.md)、[官方对账说明](https://opendocs.alipay.com/open/03axlq.md)
。日账单通常次日生成，不能以本地到点代替实际文件就绪；代码拒绝当日及未来账期。

## 申请回执与时效

`AlipayStatementGrantClient.request` 返回临时 AlipayStatementGrant，仅表示已验签的下载地址申请。官方地址有效期为 30
秒；实现从请求发出时保守计时，网络等待消耗原窗口，不从响应到达重新延期。错误响应不能生成成功账单：

| 渠道响应                                           | 渠道层结果                            |
|----------------------------------------------------|---------------------------------------|
| BILL_NOT_EXIST / NO_BILL_DATA                      | STATEMENT_NOT_FOUND                   |
| SYSTEM_RATE_LIMIT / USER_RATE_LIMIT                | STATEMENT_RATE_LIMITED                |
| TYPE_NOT_SUPPORTED                                 | UNSUPPORTED_CAPABILITY                |
| INVAILID_ARGUMENTS / BILL_DATE_BEFORE_REGISTRATION | INVALID_ARGUMENT                      |
| 其他业务/依赖错误                                  | DEPENDENCY_UNAVAILABLE                |
| 无效签名或畸形字段                                 | 原验签错误 / CHANNEL_RESPONSE_INVALID |

官方建议避免频繁申请，限流后至少间隔一分钟；当前没有新增自动重试循环。未来接入原生任务时须按该约束安排退避，不能沿用一个已过期地址重试。

`bill_file_code=EMPTY_DATA_WITH_BILL_FILE` 也必须存在有效下载地址；该标记不代替下载、完整文件校验与解析结果，不直接封存零行成功。

## 私有来源凭证

复用 AlipayProtocolCodec 的原始响应节点验签。AlipayProtocolClient.exchangeVerified 返回精确原始响应信封及完整签名表单的
SHA256，不保存表单正文。普通支付/退款 call 沿用相同执行入口和验证流程。

凭证格式 `alipay-trade-grant-v1` 为大端 int32 长度前缀字段，字符串 UTF-8，null 为
-1。字段依次为：格式、租户、bindingRevisionId、accountScopeHash、账单日、trade、appId、sellerId、可空
operatorAppId、credentialReference、credentialVersion、可空 authorizationReference、可空 authorizationVersion、API 名、请求表单
SHA256、申请绝对时间、接收绝对时间、原始已验签响应信封字节。原信封不超过 16 KiB，凭证总长不超过 32 KiB。

下载 URL 和凭证在诊断输出中隐藏，字节数组防御性复制，凭证摘要核对精确原字节。响应信封含短期下载令牌，只能作为私有证据处理。当前临时对象不会自行写
File 或数据库；后续正式来源接线才可使用已有私有 File 长期引用事务。

官方接口没有提供文件摘要字段。请求/凭证的本地 SHA256 不代表官方文件摘要，更不能伪装为微信的 SHA1 校验。正式实现需要显式表示不同来源的完整性证据。

## 下载及文件格式待确认

官方示例下载链接使用 HTTP。当前仅在隐藏的临时对象保留该文档允许的 URL；没有发起下载、自动升级/降级协议、跟随跳转或允许任意目标。实现下载前需要确认实际官方
HTTPS 协议、域名和路径，再以固定命名 HttpTemplate operation 和严格原址校验接入，不能把响应里的任意 URL 交给通用客户端。

官方允许调整语言和下载列；当前说明中的明细示例图实际展示汇总表，无法据此确定真实明细 CSV 的编码、账户/账期头行、交易/退款类型、金额符号、汇总尾行及
ZIP 文件选择。需要脱敏的真实表头、头尾文本和官方字段语义，再冻结版本化 profile；不以猜测列名接通交易核对。

已确认语义：支付宝交易号对应 trade_no，商户订单号对应 out_trade_no，订单金额为 gross total_amount，退款批次号对应退款
out_request_no。商家实收或扣费后净额不能替代应核对的原交易金额。还需明确退款明细的原交易关联、退款金额与日期口径后，才可扩展
payment 权威日范围导出。

现有微信来源 receipt、RPC、reconciliation 官方来源表和 parser 仍保留微信 profile
约束。支付宝不能绕过约束或复用微信格式名。后续一起扩展来源完整性模型、文件解析、账户目录、RPC、原件/凭证封存与测试；当前支付宝官方完整链路仍未完成。

## 测试源码与后续验收

`AlipayStatementGrantClientTest` 使用合成 RSA 签名响应及受控 HTTP/KMS，覆盖原服务商/直连范围、精确请求摘要/信封、空标记、明确业务错误、无效签名、网络耗尽原有效期、异常
URI、租户/事务防线及防御性复制。测试源码已编写，未运行。

后续需验证生成代码/装配、真实授权与 bill API、HTTPS 下载和私有存储、实际完整账单/零行/退款样本、重试与原任务 ACK，以及
File/Transfer/数据库原子性。当前不构成这些验收的通过证据。
