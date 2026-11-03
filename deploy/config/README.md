# 按环境维护配置

直接编辑 dev、test、prod 中的文件，不使用模板生成或 init。APP_ENV 选择配置目录，默认 dev。
普通配置可入库，密码、业务密钥、JWT 和签名私钥不得入库。运行脚本不会自动生成或覆盖这套配置。

```text
deploy/config/
  dev/                         # 当前 OrbStack 开发配置
    common.env                 # 七应用共享的普通参数
    jwt-public.pem             # 固定 RSA 验签公钥，可入库
    service-token-policy.json  # 开发离线签发的调用方/受众/权限清单
    customer/config.env
    customer/secret.env        # 本地填写，Git 忽略
    customer/service-tokens/   # JWT 文件 Git 忽略
    ...                        # product/limitcenter/payment/acquire/realtime/reconciliation
  test/                        # 独立配置，环境值待填写
  prod/                        # 独立配置，环境值待填写
```

common.env 与应用 config.env 合并，同名值以应用配置为准。secret.env 与合并后的普通参数不得重名。
环境文件使用字面 KEY=value，不加 shell 引号，不做变量展开，也不执行其中的命令。
共享 JWT_ISSUER_URI、JWT_PUBLIC_KEY_LOCATION 或 JWT_JWK_SET_URI 向七应用提供信任配置；应用级 <APP>_JWT_* 可覆盖。
每个应用继续使用自己的 <APP>_JWT_AUDIENCE，默认是应用名。

## 各应用必需值

每个应用 config.env 填写 <APP>_JDBC_URL、<APP>_JDBC_USERNAME，secret.env 填写 <APP>_JDBC_PASSWORD；APP 为大写应用名。
test/prod 已列出配置键，但尚未填写真实数据库或发行方地址；本机创建了待填写的忽略文件 secret.env。
新检出时手动创建各应用 secret.env，不从模板生成。其余必需项如下：

| 应用 | 额外普通配置 | 额外敏感配置 | 必需的出站令牌文件 |
| --- | --- | --- | --- |
| customer | 无 | 无 | 无 |
| product | 无 | 无 | limitcenter.jwt |
| limitcenter | 无 | LIMITCENTER_PERMIT_SECRET | payment.jwt |
| payment | 无 | PAYMENT_INPUT_FINGERPRINT_KEY | acquire.jwt、limitcenter.jwt、realtime.jwt |
| acquire | 无 | 无 | payment.jwt、realtime.jwt |
| realtime | 无 | 无 | 无 |
| reconciliation | RECONCILIATION_S3_BUCKET | 可选 RECONCILIATION_S3_ACCESS_KEY/RECONCILIATION_S3_SECRET_KEY，须成对 | payment.jwt |

两项业务密钥至少 32 个 UTF-8 字节，涉及已有金融状态时必须保持原值。S3 凭证留空时使用默认凭证链。
dev 已迁移现有 PostgreSQL、RustFS 配置、业务密钥和 8 份有效令牌；旧 local-config/ 与发布记录保留，未触碰集群。
dev 的对账账户目录仍需按真实主/子商户配置；默认空目录不授权账户。

## 固定公钥与离线 JWT

应用使用 jwt-public.pem 离线验签，不需要 JWT/JWKS 服务。公钥会加入各应用原有的可变投射 Secret，路径为 /var/run/loadup/secrets/jwt-public.pem。
公钥在启动时加载，签名、issuer、audience、有效期及 token_use 校验不关闭。
dev common.env 保留现有 JWT 的 issuer 字符串，仅用于声明比较，不需要对应 URL 可以访问。

所有应用共享该环境的验签公钥，但令牌仍按调用方和目标服务分开；一个固定 JWT 无法同时保留各应用身份和最小权限。
令牌文件在有效期内可反复使用，部署时不重新签发；到期前离线续期即可。

```bash
# 默认使用已有私钥 deploy/local-config/local-development-pki/issuer-private.pem。
# 也可通过 JWT_PRIVATE_KEY 指定受控文件。默认保留已有 JWT，显式 RENEW=1 才替换。
./scripts/dev-jwt.sh
RENEW=1 JWT_TTL_DAYS=30 ./scripts/dev-jwt.sh
./scripts/configmap.sh tokens all
```

签发脚本只允许 dev，权限由 service-token-policy.json 限定，私钥不会上传，不包含通用管理员或任意租户授权。
本机没有私钥时，先明确生成一对开发 RSA 密钥，再更新同环境公钥；不要替换已有生产签名材料：

```bash
umask 077
mkdir -p deploy/local-config/local-development-pki
# 仅全新开发环境、目标文件不存在时执行；已有密钥不覆盖。
if test ! -e deploy/local-config/local-development-pki/issuer-private.pem; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -out deploy/local-config/local-development-pki/issuer-private.pem &&
  openssl pkey -in deploy/local-config/local-development-pki/issuer-private.pem \
    -pubout -out deploy/config/dev/jwt-public.pem
fi
./scripts/dev-jwt.sh
```

test/prod 应使用各自发行方和密钥，不能复用 dev 私钥或开发令牌；可配置独立 JWKS，或明确投递本环境公钥和受控签发的令牌。
需要多公钥平滑轮换时选 JWKS；静态换钥必须协调令牌发布与应用滚动。

## 发布

```bash
APP_ENV=dev ./scripts/configmap.sh check all
APP_ENV=dev IMAGE_TAG=release-002 ./scripts/up.sh all
```

脚本根据内容计算快照版本，普通配置变化后重新执行 up 即可滚动。版本只用于集群快照与回退归档，本地目录保持 dev/test/prod。
test/prod 集群操作还需显式 KUBE_CONTEXT；当前部署 namespace 固定 loadup-pay，本地镜像发布流程不会自动推送远程镜像。
首次从旧 JWKS 方案迁移，先执行 tokens 加入公钥，再构建/发布包含本次框架变更的新镜像；旧 release-001 不能直接使用新配置。
Pay 构建仅包含本仓库模块，不会自动构建相邻 framework；须先将更新的 resource-server 及其依赖安装到本地 Maven 仓库，再执行 up。
按既有构建约定，可在 loadup-framework 目录由本地构建者执行 `mvnd clean install -pl loadup-components/security/loadup-components-resource-server -am -Dmaven.test.skip=true`。
确认所有消费者迁移并完成验收后，再退役旧 JWKS 资源；当前源码删除了旧本地服务清单，实际运行服务尚未删除。

配置管理、删除保护、回退和发布范围见 [脚本说明](../../scripts/README.md) 与 [发布约定](../CONFIGURATION.md)。
