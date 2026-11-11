# LoadUp Pay BOM

Pay 制品与配套 Framework、Modules 依赖的统一版本入口。消费方在 `dependencyManagement` 中导入：

```xml
<dependency>
    <groupId>io.github.loadup-cloud</groupId>
    <artifactId>loadup-pay-bom</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

随后按需声明具体 Pay、Framework 或 Modules 依赖，不重复写受管版本。BOM import 不会自动引入 JAR。
Framework、Modules 的配套版本在本 BOM 内分别由 `loadup.framework.version`、`loadup.modules.version` 管理，
Pay 自身制品使用 `${project.version}`。Testify 由 Pay parent 单独管理，实际测试工具依赖保持 `test` scope。

本 BOM 只显式声明 Maven Deploy 插件，通过 `spotless.skip=true`、`enforcer.skip=true` 跳过自身质量检查。
构建插件和注解处理器路径由 Pay parent 管理，BOM import 不传递这些配置或 properties。

修改导入关系后，先安装或发布更新后的上游与 Pay BOM，避免本地仓库继续使用旧 SNAPSHOT 模型。
具体分工见 [ARCHITECTURE.md](ARCHITECTURE.md)。
