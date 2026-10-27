# Reconciliation 后置交易对账

首期支付宝/微信官方交易日账单，按实际收款账户与账单日期核对收款、退款；银联待产品明确后接入。

当前仅有 `reconciliation-client`、`reconciliation-domain`：公开用例契约、完整输入封存校验、批次生命周期、独立付款/退款自然键、
纯 merge 核对、多原因差错与确定性证据摘要。已接根 reactor 与源码/POM 门禁；未构建或运行测试。

服务尚无应用、文件/Transfer 导入、持久化、HTTP/gRPC、调度或可部署 bootstrap。不可将当前源码称为已上线对账服务。
后续按 COLA 分层继续实现，复用 LoadUp File/Transfer/Database/MapStruct/DelayedTask/Outbox，不进入交易提交链路或直接写资金状态。

详细契约与 [数据库草案](../design/sql/reconciliation-draft.sql)见 [对账设计](../design/RECONCILIATION.md)。
