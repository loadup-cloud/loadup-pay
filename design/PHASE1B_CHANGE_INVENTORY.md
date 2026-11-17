# 阶段 1B 重设计逐文件影响清单

日期：2026-11-16。配套主文：[固定部署与数据源隔离设计](PHASE1B_IMPLEMENTATION.md)。**清单是下一轮实施输入，所有文件动作尚未执行；本轮不改代码、配置、POM、SQL 或测试。**

本表来自当前源码中的 tenant/context/schema 配置引用，加上显式登记的 HTTP、客户端和后台装配入口。匹配列表用于防遗漏，不等于每个文件都必须修改；KMS 原生 namespace、测试常量和无租户能力的组件应按用途审查。每项最终必须记录“已调整/删除/替换”或“保留及理由”，不能凭名称批量删除。

本清单不枚举没有直接租户引用的全部传递调用者。修改接口后，必须按符号引用同步构造器、MapStruct、手动 Bean 装配、HTTP DTO、测试夹具和依赖；主文 R3/R5 的验收以无生产残留及调用方完整为准。

## 1 拟新增文件（尚不存在，不作为现有链接）

| 所属模块 | 拟新增文件/职责 |
|---|---|
| runtime | `src/main/java/io/github/loadup/components/runtime/deployment/DeploymentIdentity.java`、`DeploymentProperties.java`、`DeploymentAutoConfiguration.java` |
| webmvc | `src/main/java/io/github/loadup/components/webmvc/DeploymentCodeFilter.java`、`DeploymentCodeWebAutoConfiguration.java`；部署错误与明确路径策略按现有 Web 错误机制放置 |
| database | `src/main/java/io/github/loadup/components/database/schema/FixedSchemaVerifier.java` 负责启动目标验证；固定连接配置扩展现有 DatabaseCoreAutoConfiguration，不新增路由 DataSource 或单独 Maven 模块 |
| 相关测试模块 | `DeploymentCodeFilterTest`、`FixedSchemaConfigurationTest`、`FixedDataSourceIsolationIT`、`DatabasePrivilegeIsolationIT` 等主文 G1B 验收入口 |

## 2 现有文件及动作

本次登记 395 个现有源码/配置/测试文件，按所属模块分组。模块标题中的 POM 也是依赖审查入口；不因命中 tenant 就删除模块。

### loadup-admin/apps/admin

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/config/app.ts](../../loadup-admin/apps/admin/src/config/app.ts) | 调整固定部署 code 来源与业务请求头/协议例外 |
| [src/request/index.ts](../../loadup-admin/apps/admin/src/request/index.ts) | 调整固定部署 code 来源与业务请求头/协议例外 |

### loadup-framework/loadup-commons/loadup-commons-context

依赖入口：[pom.xml](../../loadup-framework/loadup-commons/loadup-commons-context/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/commons/context/ContextKeys.java](../../loadup-framework/loadup-commons/loadup-commons-context/src/main/java/io/github/loadup/commons/context/ContextKeys.java) | 删除 TENANT_ID；保留 trace、请求及其他上下文键 |
| [src/main/java/io/github/loadup/commons/context/DeploymentDataScope.java](../../loadup-framework/loadup-commons/loadup-commons-context/src/main/java/io/github/loadup/commons/context/DeploymentDataScope.java) | 删除；调用方全部收口后执行 |
| [src/test/java/io/github/loadup/commons/context/ServiceTemplateTest.java](../../loadup-framework/loadup-commons/loadup-commons-context/src/test/java/io/github/loadup/commons/context/ServiceTemplateTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-commons/loadup-commons-dto

依赖入口：[pom.xml](../../loadup-framework/loadup-commons/loadup-commons-dto/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/commons/constant/CommonConstants.java](../../loadup-framework/loadup-commons/loadup-commons-dto/src/main/java/io/github/loadup/commons/constant/CommonConstants.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |

### loadup-framework/loadup-commons/loadup-commons-log

依赖入口：[pom.xml](../../loadup-framework/loadup-commons/loadup-commons-log/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/commons/log/LogContext.java](../../loadup-framework/loadup-commons/loadup-commons-log/src/main/java/io/github/loadup/commons/log/LogContext.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |

### loadup-framework/loadup-commons/loadup-commons-util

依赖入口：[pom.xml](../../loadup-framework/loadup-commons/loadup-commons-util/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/commons/util/MDCUtils.java](../../loadup-framework/loadup-commons/loadup-commons-util/src/main/java/io/github/loadup/commons/util/MDCUtils.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/commons/util/TenantUtil.java](../../loadup-framework/loadup-commons/loadup-commons-util/src/main/java/io/github/loadup/commons/util/TenantUtil.java) | 删除；调用方全部收口后执行 |
| [src/test/java/io/github/loadup/commons/util/TenantUtilTest.java](../../loadup-framework/loadup-commons/loadup-commons-util/src/test/java/io/github/loadup/commons/util/TenantUtilTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/data/loadup-components-database

依赖入口：[pom.xml](../../loadup-framework/loadup-components/data/loadup-components-database/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/database/autoconfigure/DatabaseCoreAutoConfiguration.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/autoconfigure/DatabaseCoreAutoConfiguration.java) | 去除租户解析装配；装配固定 schema 连接配置与启动验证，拒绝冲突配置 |
| [src/main/java/io/github/loadup/components/database/autoconfigure/JdbcTransactionAutoConfiguration.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/autoconfigure/JdbcTransactionAutoConfiguration.java) | 移除 tenant 包装；保留事务、重试、错误转换 |
| [src/main/java/io/github/loadup/components/database/autoconfigure/MyBatisFlexAutoConfiguration.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/autoconfigure/MyBatisFlexAutoConfiguration.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/database/autoconfigure/SequenceAutoConfiguration.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/autoconfigure/SequenceAutoConfiguration.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/database/config/DatabaseProperties.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/config/DatabaseProperties.java) | 删除 MultiTenant 与 default-schema 旧入口；新增唯一 schema 配置并校验 |
| [src/main/java/io/github/loadup/components/database/flyway/LoadUpFlywayAutoConfiguration.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/flyway/LoadUpFlywayAutoConfiguration.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/database/sequence/SegmentSequence.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/sequence/SegmentSequence.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/database/sequence/SequenceScope.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/sequence/SequenceScope.java) | 去除数据库租户维度；保留商户/服务/会话/业务范围 |
| [src/main/java/io/github/loadup/components/database/tenant/DatabaseTenant.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/tenant/DatabaseTenant.java) | 删除；调用方全部收口后执行 |
| [src/main/java/io/github/loadup/components/database/tenant/DatabaseTenantResultCodeEnum.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/tenant/DatabaseTenantResultCodeEnum.java) | 删除；调用方全部收口后执行 |
| [src/main/java/io/github/loadup/components/database/tenant/TenantContext.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/tenant/TenantContext.java) | 删除；调用方全部收口后执行 |
| [src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | 删除旧部署范围/租户自动配置登记，登记新部署或固定 schema 装配；核对条件和顺序 |
| [src/test/java/io/github/loadup/components/database/autoconfigure/DatabaseCoreAutoConfigurationTest.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/test/java/io/github/loadup/components/database/autoconfigure/DatabaseCoreAutoConfigurationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/database/flyway/FlywayAutoConfigurationTest.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/test/java/io/github/loadup/components/database/flyway/FlywayAutoConfigurationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/database/sequence/DatabaseSequenceIT.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/test/java/io/github/loadup/components/database/sequence/DatabaseSequenceIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/database/tenant/DatabaseTenantTest.java](../../loadup-framework/loadup-components/data/loadup-components-database/src/test/java/io/github/loadup/components/database/tenant/DatabaseTenantTest.java) | 删除旧职责；测试改为固定 schema/配置验收 |

### loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-api

依赖入口：[pom.xml](../../loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-api/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/scheduler/SchedulerAutoConfiguration.java](../../loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-api/src/main/java/io/github/loadup/components/scheduler/SchedulerAutoConfiguration.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/scheduler/SchedulerTemplate.java](../../loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-api/src/main/java/io/github/loadup/components/scheduler/SchedulerTemplate.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/components/scheduler/SchedulerTemplateTest.java](../../loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-api/src/test/java/io/github/loadup/components/scheduler/SchedulerTemplateTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-binder-local

依赖入口：[pom.xml](../../loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-binder-local/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/test/java/io/github/loadup/components/scheduler/local/LocalSchedulerTest.java](../../loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-binder-local/src/test/java/io/github/loadup/components/scheduler/local/LocalSchedulerTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-binder-powerjob

依赖入口：[pom.xml](../../loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-binder-powerjob/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/test/java/io/github/loadup/components/scheduler/powerjob/PowerJobSchedulerTest.java](../../loadup-framework/loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-binder-powerjob/src/test/java/io/github/loadup/components/scheduler/powerjob/PowerJobSchedulerTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-test

依赖入口：[pom.xml](../../loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-test/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/test/java/io/github/loadup/components/gotone/test/store/JdbcStoreIT.java](../../loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-test/src/test/java/io/github/loadup/components/gotone/test/store/JdbcStoreIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/integration/loadup-components-http

依赖入口：[pom.xml](../../loadup-framework/loadup-components/integration/loadup-components-http/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/http/HttpAutoConfiguration.java](../../loadup-framework/loadup-components/integration/loadup-components-http/src/main/java/io/github/loadup/components/http/HttpAutoConfiguration.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/http/HttpClientDefaults.java](../../loadup-framework/loadup-components/integration/loadup-components-http/src/main/java/io/github/loadup/components/http/HttpClientDefaults.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/http/HttpProperties.java](../../loadup-framework/loadup-components/integration/loadup-components-http/src/main/java/io/github/loadup/components/http/HttpProperties.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/http/HttpServiceClients.java](../../loadup-framework/loadup-components/integration/loadup-components-http/src/main/java/io/github/loadup/components/http/HttpServiceClients.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/http/HttpTemplate.java](../../loadup-framework/loadup-components/integration/loadup-components-http/src/main/java/io/github/loadup/components/http/HttpTemplate.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/components/http/HttpAutoConfigurationTest.java](../../loadup-framework/loadup-components/integration/loadup-components-http/src/test/java/io/github/loadup/components/http/HttpAutoConfigurationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/http/HttpServiceClientsTest.java](../../loadup-framework/loadup-components/integration/loadup-components-http/src/test/java/io/github/loadup/components/http/HttpServiceClientsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay

依赖入口：[pom.xml](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/payment/channel/alipay/AlipayPaymentAdapter.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/AlipayPaymentAdapter.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/AlipayRefundAdapter.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/AlipayRefundAdapter.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/AlipaySubmitAdapter.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/AlipaySubmitAdapter.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayAuthorizationDocument.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayAuthorizationDocument.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayBindingGuard.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayBindingGuard.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayCredentialDocument.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayCredentialDocument.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayCredentialReader.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayCredentialReader.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayCredentials.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayCredentials.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayNotificationCodec.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayNotificationCodec.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayStatementGrantClient.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/main/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayStatementGrantClient.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/test/java/io/github/loadup/components/payment/channel/alipay/AlipayPaymentAdapterTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/test/java/io/github/loadup/components/payment/channel/alipay/AlipayPaymentAdapterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/alipay/AlipayRefundAdapterTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/test/java/io/github/loadup/components/payment/channel/alipay/AlipayRefundAdapterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/alipay/AlipaySubmitAdapterTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/test/java/io/github/loadup/components/payment/channel/alipay/AlipaySubmitAdapterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayCredentialReaderTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/test/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayCredentialReaderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayNotificationCodecTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/test/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayNotificationCodecTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayStatementGrantClientTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-alipay/src/test/java/io/github/loadup/components/payment/channel/alipay/protocol/AlipayStatementGrantClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat

依赖入口：[pom.xml](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/payment/channel/wechat/WechatCodepayAdapter.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/WechatCodepayAdapter.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/WechatPaymentAdapter.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/WechatPaymentAdapter.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/WechatRefundAdapter.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/WechatRefundAdapter.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/WechatStatementProvider.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/WechatStatementProvider.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/WechatSubmitAdapter.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/WechatSubmitAdapter.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatBindingGuard.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatBindingGuard.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatCredentialDocument.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatCredentialDocument.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatCredentialReader.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatCredentialReader.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatCredentials.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatCredentials.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatJsapiSigner.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatJsapiSigner.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatNotificationCodec.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatNotificationCodec.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatStatementCodec.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatStatementCodec.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatV2Client.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/main/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatV2Client.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/WechatCodepayAdapterTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/WechatCodepayAdapterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/WechatPaymentAdapterTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/WechatPaymentAdapterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/WechatRefundAdapterTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/WechatRefundAdapterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/WechatStatementProviderTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/WechatStatementProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/WechatSubmitAdapterTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/WechatSubmitAdapterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatNotificationCodecTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatNotificationCodecTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatRefundNotificationCodecTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatRefundNotificationCodecTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatV2ClientTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatV2ClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatV2KeyReaderTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/binders/loadup-components-payment-channel-wechat/src/test/java/io/github/loadup/components/payment/channel/wechat/protocol/WechatV2KeyReaderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-api

依赖入口：[pom.xml](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-api/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/payment/channel/api/model/ChannelExecutionContext.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-api/src/main/java/io/github/loadup/components/payment/channel/api/model/ChannelExecutionContext.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/test/java/io/github/loadup/components/payment/channel/api/model/ChannelIdentityContractTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-api/src/test/java/io/github/loadup/components/payment/channel/api/model/ChannelIdentityContractTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core

依赖入口：[pom.xml](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/payment/channel/core/DefaultChannelNotificationFacade.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core/src/main/java/io/github/loadup/components/payment/channel/core/DefaultChannelNotificationFacade.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/core/DefaultChannelPaymentFacade.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core/src/main/java/io/github/loadup/components/payment/channel/core/DefaultChannelPaymentFacade.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/main/java/io/github/loadup/components/payment/channel/core/DefaultChannelStatementFacade.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core/src/main/java/io/github/loadup/components/payment/channel/core/DefaultChannelStatementFacade.java) | 调整动态 tenant 来源及 API 调用方；保留账户/凭证/渠道绑定 |
| [src/test/java/io/github/loadup/components/payment/channel/core/DefaultChannelNotificationFacadeTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core/src/test/java/io/github/loadup/components/payment/channel/core/DefaultChannelNotificationFacadeTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/core/DefaultChannelPaymentFacadeTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core/src/test/java/io/github/loadup/components/payment/channel/core/DefaultChannelPaymentFacadeTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/core/DefaultChannelStatementFacadeTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core/src/test/java/io/github/loadup/components/payment/channel/core/DefaultChannelStatementFacadeTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/payment/channel/core/FacadeEdgeCaseTest.java](../../loadup-framework/loadup-components/integration/loadup-components-payment-channel/loadup-components-payment-channel-core/src/test/java/io/github/loadup/components/payment/channel/core/FacadeEdgeCaseTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/integration/loadup-components-rpc/loadup-components-rpc-binder-dubbo

依赖入口：[pom.xml](../../loadup-framework/loadup-components/integration/loadup-components-rpc/loadup-components-rpc-binder-dubbo/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/rpc/dubbo/RpcClientDefaults.java](../../loadup-framework/loadup-components/integration/loadup-components-rpc/loadup-components-rpc-binder-dubbo/src/main/java/io/github/loadup/components/rpc/dubbo/RpcClientDefaults.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/components/rpc/dubbo/RpcClientDefaultsTest.java](../../loadup-framework/loadup-components/integration/loadup-components-rpc/loadup-components-rpc-binder-dubbo/src/test/java/io/github/loadup/components/rpc/dubbo/RpcClientDefaultsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/rpc/dubbo/RpcIdentityProviderFilterTest.java](../../loadup-framework/loadup-components/integration/loadup-components-rpc/loadup-components-rpc-binder-dubbo/src/test/java/io/github/loadup/components/rpc/dubbo/RpcIdentityProviderFilterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/platform/loadup-components-observability

依赖入口：[pom.xml](../../loadup-framework/loadup-components/platform/loadup-components-observability/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/test/java/io/github/loadup/components/observability/LoadUpContextPropagationTest.java](../../loadup-framework/loadup-components/platform/loadup-components-observability/src/test/java/io/github/loadup/components/observability/LoadUpContextPropagationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/platform/loadup-components-runtime

依赖入口：[pom.xml](../../loadup-framework/loadup-components/platform/loadup-components-runtime/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/runtime/context/DeploymentScopeAutoConfiguration.java](../../loadup-framework/loadup-components/platform/loadup-components-runtime/src/main/java/io/github/loadup/components/runtime/context/DeploymentScopeAutoConfiguration.java) | 替换为固定部署标识/请求校验及相应测试 |
| [src/main/java/io/github/loadup/components/runtime/context/DeploymentScopeProperties.java](../../loadup-framework/loadup-components/platform/loadup-components-runtime/src/main/java/io/github/loadup/components/runtime/context/DeploymentScopeProperties.java) | 替换为固定部署标识/请求校验及相应测试 |
| [src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports](../../loadup-framework/loadup-components/platform/loadup-components-runtime/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | 删除旧部署范围/租户自动配置登记，登记新部署或固定 schema 装配；核对条件和顺序 |
| [src/test/java/io/github/loadup/components/runtime/context/DeploymentScopeAutoConfigurationTest.java](../../loadup-framework/loadup-components/platform/loadup-components-runtime/src/test/java/io/github/loadup/components/runtime/context/DeploymentScopeAutoConfigurationTest.java) | 替换为固定部署标识/请求校验及相应测试 |

### loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-api

依赖入口：[pom.xml](../../loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-api/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/asyncresult/AsyncReplyKey.java](../../loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-api/src/main/java/io/github/loadup/components/asyncresult/AsyncReplyKey.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/asyncresult/AsyncResultOptions.java](../../loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-api/src/main/java/io/github/loadup/components/asyncresult/AsyncResultOptions.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/asyncresult/DefaultAsyncResultTemplate.java](../../loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-api/src/main/java/io/github/loadup/components/asyncresult/DefaultAsyncResultTemplate.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |

### loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-binder-redis

依赖入口：[pom.xml](../../loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-binder-redis/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/asyncresult/redis/RedisAsyncResultProvider.java](../../loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-binder-redis/src/main/java/io/github/loadup/components/asyncresult/redis/RedisAsyncResultProvider.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |

### loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-test

依赖入口：[pom.xml](../../loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-test/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/test/java/io/github/loadup/components/asyncresult/AsyncResultAutoConfigurationTest.java](../../loadup-framework/loadup-components/reliability/loadup-components-async-result/loadup-components-async-result-test/src/test/java/io/github/loadup/components/asyncresult/AsyncResultAutoConfigurationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/reliability/loadup-components-task

依赖入口：[pom.xml](../../loadup-framework/loadup-components/reliability/loadup-components-task/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/task/TaskAutoConfiguration.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/TaskAutoConfiguration.java) | 核对同一 DataSource/事务与后台隔离；无需 tenant 逻辑则保持 |
| [src/main/java/io/github/loadup/components/task/TaskDispatcher.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/TaskDispatcher.java) | 核对同一 DataSource/事务与后台隔离；无需 tenant 逻辑则保持 |
| [src/main/java/io/github/loadup/components/task/JdbcTaskRepository.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/JdbcTaskRepository.java) | 核对同一 DataSource/事务与后台隔离；无需 tenant 逻辑则保持 |
| [src/main/java/io/github/loadup/components/task/TaskTemplate.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/TaskTemplate.java) | 核对同一 DataSource/事务与后台隔离；无需 tenant 逻辑则保持 |
| [src/test/java/io/github/loadup/components/task/TaskWorkerTest.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/test/java/io/github/loadup/components/task/TaskWorkerTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/task/TaskIT.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/test/java/io/github/loadup/components/task/TaskIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/reliability/loadup-components-lock

依赖入口：[pom.xml](../../loadup-framework/loadup-components/reliability/loadup-components-lock/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/lock/LockKey.java](../../loadup-framework/loadup-components/reliability/loadup-components-lock/src/main/java/io/github/loadup/components/lock/LockKey.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |

### loadup-framework/loadup-components/reliability/loadup-components-task

依赖入口：[pom.xml](../../loadup-framework/loadup-components/reliability/loadup-components-task/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/task/IdempotentEventConsumer.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/IdempotentEventConsumer.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/task/JdbcTaskRepository.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/JdbcTaskRepository.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/task/TaskAutoConfiguration.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/TaskAutoConfiguration.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/task/TaskProperties.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/TaskProperties.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/components/task/TaskWorker.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/main/java/io/github/loadup/components/task/TaskWorker.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/components/task/TaskConfigurationTest.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/test/java/io/github/loadup/components/task/TaskConfigurationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/task/TaskIT.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/test/java/io/github/loadup/components/task/TaskIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/task/TaskWorkerTest.java](../../loadup-framework/loadup-components/reliability/loadup-components-task/src/test/java/io/github/loadup/components/task/TaskWorkerTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/security/loadup-components-service-auth

依赖入口：[pom.xml](../../loadup-framework/loadup-components/security/loadup-components-service-auth/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/test/java/io/github/loadup/components/serviceauth/ServiceAccessTokensTest.java](../../loadup-framework/loadup-components/security/loadup-components-service-auth/src/test/java/io/github/loadup/components/serviceauth/ServiceAccessTokensTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-framework/loadup-components/web/loadup-components-webmvc

依赖入口：[pom.xml](../../loadup-framework/loadup-components/web/loadup-components-webmvc/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/components/webmvc/DeploymentScopeFilter.java](../../loadup-framework/loadup-components/web/loadup-components-webmvc/src/main/java/io/github/loadup/components/webmvc/DeploymentScopeFilter.java) | 替换为固定部署标识/请求校验及相应测试 |
| [src/main/java/io/github/loadup/components/webmvc/DeploymentScopeWebAutoConfiguration.java](../../loadup-framework/loadup-components/web/loadup-components-webmvc/src/main/java/io/github/loadup/components/webmvc/DeploymentScopeWebAutoConfiguration.java) | 替换为固定部署标识/请求校验及相应测试 |
| [src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports](../../loadup-framework/loadup-components/web/loadup-components-webmvc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) | 删除旧部署范围/租户自动配置登记，登记新部署或固定 schema 装配；核对条件和顺序 |
| [src/test/java/io/github/loadup/components/webmvc/DeploymentScopeFilterTest.java](../../loadup-framework/loadup-components/web/loadup-components-webmvc/src/test/java/io/github/loadup/components/webmvc/DeploymentScopeFilterTest.java) | 替换为固定部署标识/请求校验及相应测试 |
| [src/test/java/io/github/loadup/components/webmvc/DeploymentScopeWebAutoConfigurationTest.java](../../loadup-framework/loadup-components/web/loadup-components-webmvc/src/test/java/io/github/loadup/components/webmvc/DeploymentScopeWebAutoConfigurationTest.java) | 替换为固定部署标识/请求校验及相应测试 |
| [src/test/java/io/github/loadup/components/webmvc/ExecutionContextFilterTest.java](../../loadup-framework/loadup-components/web/loadup-components-webmvc/src/test/java/io/github/loadup/components/webmvc/ExecutionContextFilterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/components/webmvc/MvcThreadModelTest.java](../../loadup-framework/loadup-components/web/loadup-components-webmvc/src/test/java/io/github/loadup/components/webmvc/MvcThreadModelTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-modules/loadup-modules-file/loadup-modules-file-core

依赖入口：[pom.xml](../../loadup-modules/loadup-modules-file/loadup-modules-file-core/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/modules/file/app/service/FileResourceService.java](../../loadup-modules/loadup-modules-file/loadup-modules-file-core/src/main/java/io/github/loadup/modules/file/app/service/FileResourceService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/modules/file/test/FileAutoConfigurationTest.java](../../loadup-modules/loadup-modules-file/loadup-modules-file-core/src/test/java/io/github/loadup/modules/file/test/FileAutoConfigurationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-modules/loadup-modules-upms/loadup-modules-upms-test

依赖入口：[pom.xml](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-test/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/test/java/io/github/loadup/modules/upms/SchemaIsolationIT.java](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-test/src/test/java/io/github/loadup/modules/upms/SchemaIsolationIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay

依赖入口：[pom.xml](../pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [deploy/gateway/templates/routes.yaml](../deploy/gateway/templates/routes.yaml) | 调整单值部署/schema/凭证及内部客户端配置 |
| [scripts/common.py](../scripts/common.py) | 调整部署配置渲染/校验及相应断言，保留发布权限边界 |
| [scripts/configmap.py](../scripts/configmap.py) | 调整部署配置渲染/校验及相应断言，保留发布权限边界 |
| [scripts/dev_merchant_token.py](../scripts/dev_merchant_token.py) | 调整部署配置渲染/校验及相应断言，保留发布权限边界 |
| [scripts/render_deploy.py](../scripts/render_deploy.py) | 调整部署配置渲染/校验及相应断言，保留发布权限边界 |

### loadup-pay/loadup-pay-acquire/loadup-pay-acquire-adapter

依赖入口：[pom.xml](../loadup-pay-acquire/loadup-pay-acquire-adapter/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/acquire/adapter/security/AcquireSecurityConfiguration.java](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/java/io/github/loadup/acquire/adapter/security/AcquireSecurityConfiguration.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/acquire/adapter/security/JwtAcquireActorProvider.java](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/java/io/github/loadup/acquire/adapter/security/JwtAcquireActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/acquire/adapter/security/JwtAcquireServiceActorProvider.java](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/java/io/github/loadup/acquire/adapter/security/JwtAcquireServiceActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/acquire/adapter/security/JwtMerchantNotificationActorProvider.java](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/java/io/github/loadup/acquire/adapter/security/JwtMerchantNotificationActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/resources/cashier/checkout-bridge.mjs](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/resources/cashier/checkout-bridge.mjs) | 调整固定部署 code 来源与业务请求头/协议例外 |
| [src/main/resources/cashier/checkout-core.mjs](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/resources/cashier/checkout-core.mjs) | 调整固定部署 code 来源与业务请求头/协议例外 |
| [src/main/resources/cashier/checkout.js](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/resources/cashier/checkout.js) | 调整固定部署 code 来源与业务请求头/协议例外 |
| [src/test/java/io/github/loadup/acquire/adapter/security/JwtAcquireActorProviderTest.java](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/test/java/io/github/loadup/acquire/adapter/security/JwtAcquireActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/adapter/security/JwtAcquireServiceActorProviderTest.java](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/test/java/io/github/loadup/acquire/adapter/security/JwtAcquireServiceActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/adapter/security/JwtMerchantNotificationActorProviderTest.java](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/test/java/io/github/loadup/acquire/adapter/security/JwtMerchantNotificationActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-acquire/loadup-pay-acquire-app

依赖入口：[pom.xml](../loadup-pay-acquire/loadup-pay-acquire-app/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/acquire/app/security/AcquireActor.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/security/AcquireActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/acquire/app/security/AcquireServiceActor.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/security/AcquireServiceActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/acquire/app/security/CashierActor.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/security/CashierActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/acquire/app/security/MerchantNotificationActor.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/security/MerchantNotificationActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/acquire/app/service/AcquireRecoveryOperationsService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/AcquireRecoveryOperationsService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/AllocationRecoveryQueue.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/AllocationRecoveryQueue.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/AllocationRecoveryService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/AllocationRecoveryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/CashierService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/CashierService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/CashierSummaryService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/CashierSummaryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/CheckoutAuthenticationService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/CheckoutAuthenticationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/CheckoutSessionManagementService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/CheckoutSessionManagementService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/CheckoutSessionOperations.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/CheckoutSessionOperations.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/CheckoutSessionService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/CheckoutSessionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/DirectPaymentRecoveryService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/DirectPaymentRecoveryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/FeeRefundOutcomeService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/FeeRefundOutcomeService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/MerchantNotificationEvents.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/MerchantNotificationEvents.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/MerchantNotificationOperationsService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/MerchantNotificationOperationsService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/MerchantNotificationQueryService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/MerchantNotificationQueryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/MerchantNotificationService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/MerchantNotificationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderClosureOperations.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderClosureOperations.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderClosureRecoveryService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderClosureRecoveryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderClosureService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderClosureService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderClosureTasks.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderClosureTasks.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderCreationService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderCreationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderCreationTransactions.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderCreationTransactions.java) | 移除 tenant 包装；保留事务、重试、错误转换 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderDeadlineService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderDeadlineService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderDeadlineTasks.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderDeadlineTasks.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderExtensionService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderExtensionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderPaymentScope.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderPaymentScope.java) | 去除数据库租户维度；保留商户/服务/会话/业务范围 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderSnapshotDelivery.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderSnapshotDelivery.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/OrderSnapshotEvents.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderSnapshotEvents.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationOperations.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationOperations.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationPrincipal.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationPrincipal.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/PaymentOutcomeEventService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentOutcomeEventService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/PaymentOutcomeOperations.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentOutcomeOperations.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/PaymentProgressService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentProgressService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/PaymentRecoveryService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentRecoveryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/app/service/RefundNotificationService.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/RefundNotificationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/acquire/app/service/AcquireRecoveryOperationsServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/AcquireRecoveryOperationsServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/AllocationRecoveryQueueTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/AllocationRecoveryQueueTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/AllocationRecoveryServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/AllocationRecoveryServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/CheckoutAuthenticationServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/CheckoutAuthenticationServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/CheckoutSessionManagementServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/CheckoutSessionManagementServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/MerchantNotificationEventsTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/MerchantNotificationEventsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/MerchantNotificationOperationsServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/MerchantNotificationOperationsServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/MerchantNotificationServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/MerchantNotificationServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/OrderClosureOperationsTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/OrderClosureOperationsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/OrderClosureRecoveryServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/OrderClosureRecoveryServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/OrderCreationTransactionsTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/OrderCreationTransactionsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/OrderDeadlineServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/OrderDeadlineServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/OrderDeadlineTasksTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/OrderDeadlineTasksTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/OrderSnapshotDeliveryTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/OrderSnapshotDeliveryTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/OrderSnapshotEventsTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/OrderSnapshotEventsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/PaymentRecoveryServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/PaymentRecoveryServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/app/service/RefundNotificationServiceTest.java](../loadup-pay-acquire/loadup-pay-acquire-app/src/test/java/io/github/loadup/acquire/app/service/RefundNotificationServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-acquire/loadup-pay-acquire-bootstrap

依赖入口：[pom.xml](../loadup-pay-acquire/loadup-pay-acquire-bootstrap/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/resources/application.yml](../loadup-pay-acquire/loadup-pay-acquire-bootstrap/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |

### loadup-pay/loadup-pay-acquire/loadup-pay-acquire-infrastructure

依赖入口：[pom.xml](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/acquire/infrastructure/client/CheckoutPresentationImpl.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/client/CheckoutPresentationImpl.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/infrastructure/codec/PaymentEventCodecImpl.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/codec/PaymentEventCodecImpl.java) | 调整摘要输入与生产/验证双方；保留同键改参冲突 |
| [src/main/java/io/github/loadup/acquire/infrastructure/config/CashierProperties.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/config/CashierProperties.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/infrastructure/config/CheckoutProperties.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/config/CheckoutProperties.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/infrastructure/config/MerchantNotificationProperties.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/config/MerchantNotificationProperties.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/acquire/infrastructure/notification/HttpMerchantNotificationTransport.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/notification/HttpMerchantNotificationTransport.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/acquire/infrastructure/client/AcquireRealtimeHttpClientTest.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/client/AcquireRealtimeHttpClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/infrastructure/client/PaymentAcceptanceHttpClientTest.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/client/PaymentAcceptanceHttpClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/infrastructure/codec/PaymentEventCodecImplTest.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/codec/PaymentEventCodecImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/infrastructure/notification/HttpMerchantNotificationTransportTest.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/notification/HttpMerchantNotificationTransportTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/infrastructure/repository/AcquiringFinancialPersistenceIT.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/repository/AcquiringFinancialPersistenceIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/infrastructure/repository/OrderFeeGatewayImplTest.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/repository/OrderFeeGatewayImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/acquire/infrastructure/security/HmacCheckoutTokensTest.java](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/security/HmacCheckoutTokensTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/resources/application-ci.yml](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/resources/application-ci.yml) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/resources/application-test.yml](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/resources/application-test.yml) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-admin

依赖入口：[pom.xml](../loadup-pay-admin/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/resources/application.yml](../loadup-pay-admin/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |

### loadup-pay/loadup-pay-limitcenter/loadup-pay-limitcenter-adapter

依赖入口：[pom.xml](../loadup-pay-limitcenter/loadup-pay-limitcenter-adapter/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/limitcenter/adapter/security/JwtLimitActorProvider.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-adapter/src/main/java/io/github/loadup/limitcenter/adapter/security/JwtLimitActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/test/java/io/github/loadup/limitcenter/adapter/security/JwtLimitActorProviderTest.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-adapter/src/test/java/io/github/loadup/limitcenter/adapter/security/JwtLimitActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-limitcenter/loadup-pay-limitcenter-app

依赖入口：[pom.xml](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/limitcenter/app/security/LimitActor.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/security/LimitActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/limitcenter/app/service/LimitExecutionService.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitExecutionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/limitcenter/app/service/LimitPolicyService.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitPolicyService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/limitcenter/app/service/LimitRefundService.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitRefundService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/limitcenter/app/service/LimitReservationService.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitReservationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/limitcenter/app/service/LimitSettlementService.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitSettlementService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/limitcenter/app/service/LimitTransactions.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitTransactions.java) | 移除 tenant 包装；保留事务、重试、错误转换 |
| [src/test/java/io/github/loadup/limitcenter/app/service/LimitTransactionsTest.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/test/java/io/github/loadup/limitcenter/app/service/LimitTransactionsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-limitcenter/loadup-pay-limitcenter-bootstrap

依赖入口：[pom.xml](../loadup-pay-limitcenter/loadup-pay-limitcenter-bootstrap/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/resources/application.yml](../loadup-pay-limitcenter/loadup-pay-limitcenter-bootstrap/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |

### loadup-pay/loadup-pay-limitcenter/loadup-pay-limitcenter-infrastructure

依赖入口：[pom.xml](../loadup-pay-limitcenter/loadup-pay-limitcenter-infrastructure/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/limitcenter/infrastructure/codec/LimitPolicyCodec.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-infrastructure/src/main/java/io/github/loadup/limitcenter/infrastructure/codec/LimitPolicyCodec.java) | 调整摘要输入与生产/验证双方；保留同键改参冲突 |
| [src/main/java/io/github/loadup/limitcenter/infrastructure/security/HmacExecutionPermitIssuer.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-infrastructure/src/main/java/io/github/loadup/limitcenter/infrastructure/security/HmacExecutionPermitIssuer.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/test/java/io/github/loadup/limitcenter/infrastructure/client/PaymentEvidenceHttpClientTest.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-infrastructure/src/test/java/io/github/loadup/limitcenter/infrastructure/client/PaymentEvidenceHttpClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/limitcenter/infrastructure/codec/LimitPolicyCodecTest.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-infrastructure/src/test/java/io/github/loadup/limitcenter/infrastructure/codec/LimitPolicyCodecTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/limitcenter/infrastructure/security/HmacExecutionPermitIssuerTest.java](../loadup-pay-limitcenter/loadup-pay-limitcenter-infrastructure/src/test/java/io/github/loadup/limitcenter/infrastructure/security/HmacExecutionPermitIssuerTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-merchantcenter/loadup-pay-merchantcenter-bootstrap

依赖入口：[pom.xml](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-bootstrap/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/merchantcenter/adapter/security/JwtCustomerActorProvider.java](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-bootstrap/src/main/java/io/github/loadup/merchantcenter/adapter/security/JwtCustomerActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/resources/application.yml](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-bootstrap/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |
| [src/test/java/io/github/loadup/merchantcenter/adapter/security/JwtCustomerActorProviderTest.java](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-bootstrap/src/test/java/io/github/loadup/merchantcenter/adapter/security/JwtCustomerActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/merchantcenter/adapter/security/MerchantEligibilityHttpSecurityTest.java](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-bootstrap/src/test/java/io/github/loadup/merchantcenter/adapter/security/MerchantEligibilityHttpSecurityTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-merchantcenter/loadup-pay-merchantcenter-service

依赖入口：[pom.xml](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-service/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/merchantcenter/app/security/CustomerActor.java](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-service/src/main/java/io/github/loadup/merchantcenter/app/security/CustomerActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/merchantcenter/app/service/CustomerCommandExecutor.java](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-service/src/main/java/io/github/loadup/merchantcenter/app/service/CustomerCommandExecutor.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/merchantcenter/app/service/CustomerService.java](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-service/src/main/java/io/github/loadup/merchantcenter/app/service/CustomerService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/merchantcenter/app/service/CustomerServiceTest.java](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-service/src/test/java/io/github/loadup/merchantcenter/app/service/CustomerServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/merchantcenter/infrastructure/repository/MerchantAuthorizationGatewayImplTest.java](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-service/src/test/java/io/github/loadup/merchantcenter/infrastructure/repository/MerchantAuthorizationGatewayImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-notificationcenter

依赖入口：[pom.xml](../loadup-pay-notificationcenter/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/notificationcenter/NotificationCenterSecurityConfiguration.java](../loadup-pay-notificationcenter/src/main/java/io/github/loadup/notificationcenter/NotificationCenterSecurityConfiguration.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/notificationcenter/NotificationCenterTenantFilter.java](../loadup-pay-notificationcenter/src/main/java/io/github/loadup/notificationcenter/NotificationCenterTenantFilter.java) | 重命名/迁入安全链；保留 JWT 守卫，删除 tenant 前提 |
| [src/main/resources/application.yml](../loadup-pay-notificationcenter/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |
| [src/test/java/io/github/loadup/notificationcenter/NotificationCenterSecurityConfigurationTest.java](../loadup-pay-notificationcenter/src/test/java/io/github/loadup/notificationcenter/NotificationCenterSecurityConfigurationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/notificationcenter/NotificationCenterTenantFilterTest.java](../loadup-pay-notificationcenter/src/test/java/io/github/loadup/notificationcenter/NotificationCenterTenantFilterTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-oauth2-server

依赖入口：[pom.xml](../loadup-pay-oauth2-server/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/test/java/io/github/loadup/oauth2server/OAuth2AuthorizationFlowIT.java](../loadup-pay-oauth2-server/src/test/java/io/github/loadup/oauth2server/OAuth2AuthorizationFlowIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-payment/loadup-pay-payment-adapter

依赖入口：[pom.xml](../loadup-pay-payment/loadup-pay-payment-adapter/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/payment/adapter/security/JwtPaymentActorProvider.java](../loadup-pay-payment/loadup-pay-payment-adapter/src/main/java/io/github/loadup/payment/adapter/security/JwtPaymentActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/payment/adapter/security/JwtRefundActorProvider.java](../loadup-pay-payment/loadup-pay-payment-adapter/src/main/java/io/github/loadup/payment/adapter/security/JwtRefundActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/test/java/io/github/loadup/payment/adapter/security/JwtPaymentActorProviderTest.java](../loadup-pay-payment/loadup-pay-payment-adapter/src/test/java/io/github/loadup/payment/adapter/security/JwtPaymentActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/adapter/security/JwtRefundActorProviderTest.java](../loadup-pay-payment/loadup-pay-payment-adapter/src/test/java/io/github/loadup/payment/adapter/security/JwtRefundActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-payment/loadup-pay-payment-app

依赖入口：[pom.xml](../loadup-pay-payment/loadup-pay-payment-app/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/payment/app/model/ChannelCallbackEndpoint.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/model/ChannelCallbackEndpoint.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/model/PaymentSubmissionTicket.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/model/PaymentSubmissionTicket.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/security/PaymentActor.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/security/PaymentActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/payment/app/security/RefundActor.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/security/RefundActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/payment/app/service/ChannelBindingService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/ChannelBindingService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentAcceptanceService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentAcceptanceService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentAcceptanceTransactions.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentAcceptanceTransactions.java) | 移除 tenant 包装；保留事务、重试、错误转换 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentActionService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentActionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentAttemptService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentAttemptService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentCaptureExecutionService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentCaptureExecutionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentCaptureService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentCaptureService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentChannelCallbackService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentChannelCallbackService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentChannelRequestFactory.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentChannelRequestFactory.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentCloseService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentCloseService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentControlService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentControlService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentEvents.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentEvents.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentExecutionService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentExecutionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentInitialAttemptService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentInitialAttemptService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentInputAccess.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentInputAccess.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentInputPublication.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentInputPublication.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentInputService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentInputService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentOutcomeDeliverySupport.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentOutcomeDeliverySupport.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentRealtimeDelivery.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentRealtimeDelivery.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentRecoveryOperationsService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentRecoveryOperationsService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentRecoveryService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentRecoveryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentResultService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentResultService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentSettlementFactsService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentSettlementFactsService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentStatementService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentStatementService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentSubmissionPreparation.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentSubmissionPreparation.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentUnknownRecoveryService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentUnknownRecoveryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/PaymentVerificationService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentVerificationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RecoveryInventoryCollector.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RecoveryInventoryCollector.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundChannelCallbackService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundChannelCallbackService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundExecutionService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundExecutionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundFeeDelivery.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundFeeDelivery.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundLimitDelivery.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundLimitDelivery.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundMerchantDelivery.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundMerchantDelivery.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundPlanningService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundPlanningService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundProjectionEvents.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundProjectionEvents.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundRecoveryService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundRecoveryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundResultService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundResultService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundSettlementFactsService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundSettlementFactsService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RefundSummaryFactsService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundSummaryFactsService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/app/service/RoutingProfileService.java](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RoutingProfileService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentAcceptanceServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentAcceptanceServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentAcceptanceTransactionsTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentAcceptanceTransactionsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentActionServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentActionServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentAttemptServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentAttemptServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentCaptureServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentCaptureServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentChannelCallbackServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentChannelCallbackServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentChannelRequestFactoryTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentChannelRequestFactoryTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentCloseServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentCloseServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentControlServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentControlServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentEventsTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentEventsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentInputPublicationTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentInputPublicationTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentOutcomeDeliverySupportTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentOutcomeDeliverySupportTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentRealtimeDeliveryTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentRealtimeDeliveryTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentRecoveryOperationsServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentRecoveryOperationsServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentRecoveryServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentRecoveryServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentStatementServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentStatementServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentUnknownRecoveryServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentUnknownRecoveryServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/PaymentVerificationServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/PaymentVerificationServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RecoveryInventoryCollectorTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RecoveryInventoryCollectorTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RefundChannelCallbackServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RefundChannelCallbackServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RefundFeeDeliveryTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RefundFeeDeliveryTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RefundLimitDeliveryTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RefundLimitDeliveryTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RefundMerchantDeliveryTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RefundMerchantDeliveryTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RefundPlanningServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RefundPlanningServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RefundProjectionEventsTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RefundProjectionEventsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RefundRealtimeDeliveryTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RefundRealtimeDeliveryTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/app/service/RefundRecoveryServiceTest.java](../loadup-pay-payment/loadup-pay-payment-app/src/test/java/io/github/loadup/payment/app/service/RefundRecoveryServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-payment/loadup-pay-payment-bootstrap

依赖入口：[pom.xml](../loadup-pay-payment/loadup-pay-payment-bootstrap/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/resources/application.yml](../loadup-pay-payment/loadup-pay-payment-bootstrap/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |

### loadup-pay/loadup-pay-payment/loadup-pay-payment-infrastructure

依赖入口：[pom.xml](../loadup-pay-payment/loadup-pay-payment-infrastructure/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/payment/infrastructure/channel/PaymentChannelGatewayImpl.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/main/java/io/github/loadup/payment/infrastructure/channel/PaymentChannelGatewayImpl.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/infrastructure/channel/PaymentChannelNotificationGatewayImpl.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/main/java/io/github/loadup/payment/infrastructure/channel/PaymentChannelNotificationGatewayImpl.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/infrastructure/codec/ChannelBindingCodecImpl.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/main/java/io/github/loadup/payment/infrastructure/codec/ChannelBindingCodecImpl.java) | 调整摘要输入与生产/验证双方；保留同键改参冲突 |
| [src/main/java/io/github/loadup/payment/infrastructure/codec/RoutingProfileCodecImpl.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/main/java/io/github/loadup/payment/infrastructure/codec/RoutingProfileCodecImpl.java) | 调整摘要输入与生产/验证双方；保留同键改参冲突 |
| [src/main/java/io/github/loadup/payment/infrastructure/config/PaymentInputProperties.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/main/java/io/github/loadup/payment/infrastructure/config/PaymentInputProperties.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/payment/infrastructure/security/KmsPaymentInputCipher.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/main/java/io/github/loadup/payment/infrastructure/security/KmsPaymentInputCipher.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/test/java/io/github/loadup/payment/infrastructure/channel/PaymentChannelGatewayImplTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/channel/PaymentChannelGatewayImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/channel/PaymentChannelNotificationGatewayImplTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/channel/PaymentChannelNotificationGatewayImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/client/PaymentAcquireHttpClientTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/client/PaymentAcquireHttpClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/client/PaymentLimitHttpClientTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/client/PaymentLimitHttpClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/client/PaymentRealtimeHttpClientTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/client/PaymentRealtimeHttpClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/codec/ChannelBindingCodecImplTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/codec/ChannelBindingCodecImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/codec/PaymentOutcomeEnvelopeCodecImplTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/codec/PaymentOutcomeEnvelopeCodecImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/codec/RoutingProfileCodecImplTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/codec/RoutingProfileCodecImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/repository/PaymentResultPersistenceIT.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/repository/PaymentResultPersistenceIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/repository/RefundAcceptanceGatewayImplTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/repository/RefundAcceptanceGatewayImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/repository/RefundCallbackTargetGatewayImplTest.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/repository/RefundCallbackTargetGatewayImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/payment/infrastructure/repository/RefundRecoveryHandlerPersistenceIT.java](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/java/io/github/loadup/payment/infrastructure/repository/RefundRecoveryHandlerPersistenceIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/resources/application-ci.yml](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/resources/application-ci.yml) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/resources/application-test.yml](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/test/resources/application-test.yml) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-productcenter/loadup-pay-productcenter-adapter

依赖入口：[pom.xml](../loadup-pay-productcenter/loadup-pay-productcenter-adapter/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/productcenter/adapter/security/JwtProductActorProvider.java](../loadup-pay-productcenter/loadup-pay-productcenter-adapter/src/main/java/io/github/loadup/productcenter/adapter/security/JwtProductActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/productcenter/adapter/security/JwtPublicationActorProvider.java](../loadup-pay-productcenter/loadup-pay-productcenter-adapter/src/main/java/io/github/loadup/productcenter/adapter/security/JwtPublicationActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/test/java/io/github/loadup/productcenter/adapter/security/JwtProductActorProviderTest.java](../loadup-pay-productcenter/loadup-pay-productcenter-adapter/src/test/java/io/github/loadup/productcenter/adapter/security/JwtProductActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/productcenter/adapter/security/JwtPublicationActorProviderTest.java](../loadup-pay-productcenter/loadup-pay-productcenter-adapter/src/test/java/io/github/loadup/productcenter/adapter/security/JwtPublicationActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-productcenter/loadup-pay-productcenter-app

依赖入口：[pom.xml](../loadup-pay-productcenter/loadup-pay-productcenter-app/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/productcenter/app/security/ProductActor.java](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/security/ProductActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/productcenter/app/security/PublicationActor.java](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/security/PublicationActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/productcenter/app/service/OrderAdmissionService.java](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/service/OrderAdmissionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/productcenter/app/service/PolicyPublicationService.java](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/service/PolicyPublicationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/productcenter/app/service/PublicationTransactions.java](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/service/PublicationTransactions.java) | 移除 tenant 包装；保留事务、重试、错误转换 |
| [src/test/java/io/github/loadup/productcenter/app/service/PublicationTransactionsTest.java](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/test/java/io/github/loadup/productcenter/app/service/PublicationTransactionsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-productcenter/loadup-pay-productcenter-bootstrap

依赖入口：[pom.xml](../loadup-pay-productcenter/loadup-pay-productcenter-bootstrap/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/resources/application.yml](../loadup-pay-productcenter/loadup-pay-productcenter-bootstrap/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |

### loadup-pay/loadup-pay-productcenter/loadup-pay-productcenter-infrastructure

依赖入口：[pom.xml](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/productcenter/infrastructure/client/LimitPolicyActivationHttpClient.java](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/src/main/java/io/github/loadup/productcenter/infrastructure/client/LimitPolicyActivationHttpClient.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/productcenter/infrastructure/codec/PolicyPublicationCodecImpl.java](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/src/main/java/io/github/loadup/productcenter/infrastructure/codec/PolicyPublicationCodecImpl.java) | 调整摘要输入与生产/验证双方；保留同键改参冲突 |
| [src/main/java/io/github/loadup/productcenter/infrastructure/outbox/PolicyActivationTaskHandler.java](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/src/main/java/io/github/loadup/productcenter/infrastructure/outbox/PolicyActivationTaskHandler.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/productcenter/infrastructure/repository/CurrentContractGatewayImpl.java](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/src/main/java/io/github/loadup/productcenter/infrastructure/repository/CurrentContractGatewayImpl.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/productcenter/infrastructure/client/LimitPolicyActivationHttpClientTest.java](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/src/test/java/io/github/loadup/productcenter/infrastructure/client/LimitPolicyActivationHttpClientTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/productcenter/infrastructure/codec/PolicyPublicationCodecImplTest.java](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/src/test/java/io/github/loadup/productcenter/infrastructure/codec/PolicyPublicationCodecImplTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/productcenter/infrastructure/outbox/PolicyActivationTaskHandlerTest.java](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/src/test/java/io/github/loadup/productcenter/infrastructure/outbox/PolicyActivationTaskHandlerTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-realtime/loadup-pay-realtime-bootstrap

依赖入口：[pom.xml](../loadup-pay-realtime/loadup-pay-realtime-bootstrap/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/realtime/adapter/security/JwtRealtimeActorProvider.java](../loadup-pay-realtime/loadup-pay-realtime-bootstrap/src/main/java/io/github/loadup/realtime/adapter/security/JwtRealtimeActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/realtime/adapter/security/RealtimeSecurityConfiguration.java](../loadup-pay-realtime/loadup-pay-realtime-bootstrap/src/main/java/io/github/loadup/realtime/adapter/security/RealtimeSecurityConfiguration.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/resources/application.yml](../loadup-pay-realtime/loadup-pay-realtime-bootstrap/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |
| [src/test/java/io/github/loadup/realtime/adapter/security/JwtRealtimeActorProviderTest.java](../loadup-pay-realtime/loadup-pay-realtime-bootstrap/src/test/java/io/github/loadup/realtime/adapter/security/JwtRealtimeActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-realtime/loadup-pay-realtime-service

依赖入口：[pom.xml](../loadup-pay-realtime/loadup-pay-realtime-service/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/realtime/app/security/RealtimeActor.java](../loadup-pay-realtime/loadup-pay-realtime-service/src/main/java/io/github/loadup/realtime/app/security/RealtimeActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/realtime/app/service/MerchantTradeQueryService.java](../loadup-pay-realtime/loadup-pay-realtime-service/src/main/java/io/github/loadup/realtime/app/service/MerchantTradeQueryService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/realtime/app/service/RealtimeTransactions.java](../loadup-pay-realtime/loadup-pay-realtime-service/src/main/java/io/github/loadup/realtime/app/service/RealtimeTransactions.java) | 移除 tenant 包装；保留事务、重试、错误转换 |
| [src/main/java/io/github/loadup/realtime/app/service/TradeProjectionService.java](../loadup-pay-realtime/loadup-pay-realtime-service/src/main/java/io/github/loadup/realtime/app/service/TradeProjectionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/realtime/infrastructure/codec/TradeProjectionCodecImpl.java](../loadup-pay-realtime/loadup-pay-realtime-service/src/main/java/io/github/loadup/realtime/infrastructure/codec/TradeProjectionCodecImpl.java) | 调整摘要输入与生产/验证双方；保留同键改参冲突 |
| [src/test/java/io/github/loadup/realtime/app/service/RealtimeTransactionsTest.java](../loadup-pay-realtime/loadup-pay-realtime-service/src/test/java/io/github/loadup/realtime/app/service/RealtimeTransactionsTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/resources/application-ci.yml](../loadup-pay-realtime/loadup-pay-realtime-service/src/test/resources/application-ci.yml) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/resources/application-test.yml](../loadup-pay-realtime/loadup-pay-realtime-service/src/test/resources/application-test.yml) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-reconciliation/loadup-pay-reconciliation-adapter

依赖入口：[pom.xml](../loadup-pay-reconciliation/loadup-pay-reconciliation-adapter/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/reconciliation/adapter/security/JwtReconciliationActorProvider.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-adapter/src/main/java/io/github/loadup/reconciliation/adapter/security/JwtReconciliationActorProvider.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/reconciliation/adapter/security/ReconciliationSecurityConfiguration.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-adapter/src/main/java/io/github/loadup/reconciliation/adapter/security/ReconciliationSecurityConfiguration.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/test/java/io/github/loadup/reconciliation/adapter/security/JwtReconciliationActorProviderTest.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-adapter/src/test/java/io/github/loadup/reconciliation/adapter/security/JwtReconciliationActorProviderTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-reconciliation/loadup-pay-reconciliation-app

依赖入口：[pom.xml](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/reconciliation/app/security/ReconciliationActor.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/security/ReconciliationActor.java) | 调整身份或固定安全域；禁止删除凭证/权限/归属校验 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationExecutionService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationExecutionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationImportExecutionService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationImportExecutionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationImportService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationImportService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationLedgerService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationLedgerService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationReviewService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationReviewService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationStatementExecutionService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationStatementExecutionService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationStatementService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationStatementService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationTransactions.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationTransactions.java) | 移除 tenant 包装；保留事务、重试、错误转换 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationVerificationDelivery.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationVerificationDelivery.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationVerificationService.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationVerificationService.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/reconciliation/app/service/ReconciliationImportServiceTest.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/test/java/io/github/loadup/reconciliation/app/service/ReconciliationImportServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/reconciliation/app/service/ReconciliationServiceTest.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/test/java/io/github/loadup/reconciliation/app/service/ReconciliationServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/reconciliation/app/service/ReconciliationStatementServiceTest.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/test/java/io/github/loadup/reconciliation/app/service/ReconciliationStatementServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/reconciliation/app/service/ReconciliationVerificationServiceTest.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-app/src/test/java/io/github/loadup/reconciliation/app/service/ReconciliationVerificationServiceTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

### loadup-pay/loadup-pay-reconciliation/loadup-pay-reconciliation-bootstrap

依赖入口：[pom.xml](../loadup-pay-reconciliation/loadup-pay-reconciliation-bootstrap/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/resources/application.yml](../loadup-pay-reconciliation/loadup-pay-reconciliation-bootstrap/src/main/resources/application.yml) | 调整单值部署/schema/凭证及内部客户端配置 |

### loadup-pay/loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure

依赖入口：[pom.xml](../loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure/pom.xml)。按主文第 7 节核对直接依赖和 scope。

| 现有文件（相对模块） | 后续动作 |
|---|---|
| [src/main/java/io/github/loadup/reconciliation/infrastructure/client/ConfiguredReconciliationAccountDirectory.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure/src/main/java/io/github/loadup/reconciliation/infrastructure/client/ConfiguredReconciliationAccountDirectory.java) | 调整租户读取/参数/配置调用；非租户业务职责保留 |
| [src/test/java/io/github/loadup/reconciliation/infrastructure/client/ConfiguredReconciliationAccountDirectoryTest.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure/src/test/java/io/github/loadup/reconciliation/infrastructure/client/ConfiguredReconciliationAccountDirectoryTest.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/java/io/github/loadup/reconciliation/infrastructure/repository/ReconciliationImportPersistenceIT.java](../loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure/src/test/java/io/github/loadup/reconciliation/infrastructure/repository/ReconciliationImportPersistenceIT.java) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/resources/application-ci.yml](../loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure/src/test/resources/application-ci.yml) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |
| [src/test/resources/application-test.yml](../loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure/src/test/resources/application-test.yml) | 调整夹具/断言并保留原业务反例；补固定隔离用例 |

## 3 装配资源与部署配置全量收口

除上述命中文件外，必须同步以下资源集；这些是明确的范围约束，不能只修改样例环境。

- runtime/database/webmvc 对应 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：移除旧类名，登记唯一的新装配入口。
- 各 Pay 应用的 `src/main/resources/application*.yml` 及 `deploy/config/{dev,test,prod}/<app>/config.env`：一份部署配置只允许一个 code/schema；敏感凭证继续使用原 Secret 机制。
- payment/acquire 等应用 `src/main/resources/db/migration/pay/<app>/`：可靠性表与业务表同 schema；本轮不运行迁移，不处理旧数据。
- `loadup-pay/scripts/tests/` 的配置/部署渲染回归、各模块自动配置测试及未显式引用 tenant 的构造调用：随接口/配置同步。
- Admin 部署环境和收银台 HTML/bootstrap：固定 code 由当前部署提供，不信任任意 URL 参数或用户可切换的本地存储。
- 网关路由、应用内部基址、issuer、JWKS、OAuth 客户端凭证、部署 namespace 和网络策略：按单套部署绑定；无数据库应用不注入数据库参数。

## 4 文档及阶段状态收口

- Framework 与 Modules 各受影响模块 README/ARCHITECTURE 在实施后更新真实能力；本次只重订设计，不提前声称固定隔离已实现。
- Pay 总体架构、演进设计、ROADMAP 链接本方案并区分“旧 1B 已有源码”与“新 1B 尚未实施”。
- 阶段 0 文件是历史采样，保留事实定位；阶段 3 中关于 tenant 摘要、身份及回执的描述，在 R3 实施后同步新输入和验证双方，不增加旧摘要兼容读取。
- 本表的路径存在性与文档链接可以静态验证；不等同于实现、编译、测试、权限或金融事务验收。
