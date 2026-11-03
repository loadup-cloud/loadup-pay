# 七应用配置模板

这些是可入库的空值模板；填写 deploy/local-config/ 下的本地副本，保留本目录的 .example 文件为空值。
模板不创建数据库、bucket、账户授权或 JWT，也不创建集群资源。应用仍沿用 application.yml 中的其余默认配置。

## 当前可编辑目录

已准备 deploy/local-config/cfg-default-sec-default/，包含 customer、product、limitcenter、payment、acquire、realtime、reconciliation 七个子目录。
其中六个已有应用保留从 Deployment 迁移的运行参数；对账新增默认模板，已有本地填写内容不覆盖。
目录和配置文件分别为 0700 / 0600，整个 local-config/ 已被 Git 忽略。

| 文件或目录 | 手动填写内容 |
| --- | --- |
| config.env | JDBC URL/用户名、JWT issuer/JWK Set URI；对账还需私有 S3 bucket |
| secret.env | JDBC 密码；limitcenter 的 permit 密钥、payment 的输入指纹密钥；对账可选 S3 凭证对 |
| required-token-files.txt | 当前默认出站调用所需文件名，空文件表示无文件要求 |
| service-tokens/ | 放入清单要求的有效 SERVICE JWT 文件；不放说明文件、密码或私钥 |

config.env 中的 audience、服务 DNS 地址和线程模式已给出当前应用默认值；按实际环境修改。
环境文件使用字面值，不使用 shell 引号或变量展开，避免密码被错误解释。
对账账户目录默认保持空，即不授权任何账户；实际账户目录配置见 [对账服务](../../reconciliation/README.md)。
密钥格式、已有金融状态的兼容性及服务令牌权限仍须按对应模块契约配置；不要以新随机值替换已有持久状态依赖的密钥。

## 初始化另一批本地配置

在 loadup-pay 根目录执行。cp -n 保留已有本地文件，命令不创建集群对象：

```bash
umask 077
config_dir=deploy/local-config/cfg-default-sec-default
for app in customer product limitcenter payment acquire realtime reconciliation; do
  mkdir -p "$config_dir/$app/service-tokens"
  cp -n "deploy/config-templates/$app/config.env.example" "$config_dir/$app/config.env"
  cp -n "deploy/config-templates/$app/secret.env.example" "$config_dir/$app/secret.env"
  cp -n "deploy/config-templates/$app/required-token-files.txt" "$config_dir/$app/required-token-files.txt"
done
```

手动补齐后，可先检查是否还有空值；该命令只显示空项名称，不显示已填写值：

```bash
rg -n '^[A-Z][A-Z0-9_]*=[[:space:]]*$' deploy/local-config/cfg-default-sec-default/*/*.env
```

随后按 [配置资源准备](../CONFIGURATION.md#准备快照) 创建对应版本的不可变 ConfigMap、不可变环境 Secret 和可变服务令牌 Secret。
仅填写本地文件不会自动创建这些资源；完成资源准备后再执行原发布命令：

```bash
IMAGE_TAG=release CONFIG_VERSION=cfg-default SECRET_VERSION=sec-default ./scripts/deploy.sh all
```

ConfigMap/Secret 已存在时不要覆盖或删后重建同一版本；换版使用新版本名称。当前模板没有运行或业务验收通过的含义。
