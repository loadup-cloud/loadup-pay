# LoadUp Pay Contracts Commercial

供产品与收单服务共享的不可变商业合约引用，不包含服务 client、domain 或实现依赖。

```xml
<dependency>
    <groupId>io.github.loadup-cloud</groupId>
    <artifactId>loadup-pay-contracts-commercial</artifactId>
</dependency>
```

`io.github.loadup.contracts.commercial.ContractReference` 保留 contractId、revision、generation、snapshotHash、compiledTermsHash
及原有结构校验和 JSON 表示。它是商业事实引用，不放入通用 commons 或只容纳事件的 pay-contracts-events。
