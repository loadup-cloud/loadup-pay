# Pay BOM Architecture

Pay BOM 独立声明发布坐标，不继承 Pay parent。parent 导入自身同版本 BOM，避免 parent 与 BOM 相互继承、导入造成模型循环。

| 版本范围 | 管理位置 |
|---|---|
| 本仓库 Pay 制品 | Pay BOM，使用自身 `${project.version}` |
| 配套 Framework | Pay BOM 导入 Framework BOM，使用 `loadup.framework.version` |
| 配套通用业务模块 | Pay BOM 导入 Modules BOM，使用 `loadup.modules.version` |
| 内部测试工具 | Pay parent 导入 Testify BOM，实际依赖为 `test` scope |
| 编译、注解处理与质量检查 | Pay parent 的构建插件配置 |

Framework BOM 优先于 Modules BOM 导入，使 Pay 显式选定的框架版本组合优先。Modules、Testify BOM 也各自导入自身实现所需的 Framework BOM，
但公开 Pay BOM 不包含 Testify，内部测试的版本管理不会随 Pay BOM 提供给消费方。

注解处理器路径由 parent 声明；MapStruct、Spring Boot 配置处理器和 database processor 的版本都从有效 dependencyManagement 解析。
import 不传递 properties，因此 parent 不复制 BOM 内的上游版本属性，避免注解处理器版本与运行时框架版本分别维护。

本 BOM 只显式声明 Deploy 插件；原生 skip 属性仅控制 BOM 自身，不影响消费方业务模块。
