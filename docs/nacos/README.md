# Nacos 配置中心导入说明

项目默认从 Nacos 加载两层配置：

- `common.yaml`
- `${spring.application.name}.yaml`

所有服务默认读取：

- `group`：`DEFAULT_GROUP`，可通过 `NACOS_GROUP` 覆盖
- `namespace`：默认 public，可通过 `NACOS_NAMESPACE` 覆盖
- `server`：`127.0.0.1:8848`，可通过 `NACOS_SERVER_ADDR` 覆盖

> Nacos 2.x 提醒：
> 当 Java 服务运行在容器网络之外（例如本机 IDE 直接启动服务）时，除了 `8848`，还要确保 `9848`、`9849` 已暴露。
> `8848` 用于 HTTP API / 控制台，`9848/9849` 用于 Nacos Java Client 的 gRPC 通道。

## 1. 需要导入的 Data ID

进入 `http://127.0.0.1:8848/nacos`，在 `DEFAULT_GROUP` 下创建以下 `YAML` 配置：

- `common.yaml`
- `gateway-service.yaml`
- `auth-service.yaml`
- `user-service.yaml`
- `question-service.yaml`
- `exam-service.yaml`
- `grading-service.yaml`
- `analysis-service.yaml`
- `admin-service.yaml`

内容模板直接使用当前目录下对应文件。

## 2. 脚本导入（推荐）

### 2.1 Windows PowerShell

```powershell
.\docs\nacos\import-nacos.ps1 `
  -NacosAddr "http://127.0.0.1:8848" `
  -Group "DEFAULT_GROUP" `
  -Username "nacos" `
  -Password "<你的 Nacos 密码>"
```

如果使用 namespace，可额外传入：

```powershell
-Namespace "<namespace-id>"
```

### 2.2 Linux/macOS/Git Bash

```bash
chmod +x docs/nacos/import-nacos.sh
NACOS_ADDR="http://127.0.0.1:8848" \
NACOS_GROUP="DEFAULT_GROUP" \
NACOS_USERNAME="nacos" \
NACOS_PASSWORD="<你的 Nacos 密码>" \
./docs/nacos/import-nacos.sh
```

如果使用 namespace，可再设置 `NACOS_NAMESPACE=<namespace-id>`。

## 3. 手工 CLI 导入（可选）

```bash
NACOS_ADDR="http://127.0.0.1:8848"
GROUP="DEFAULT_GROUP"
for f in common.yaml gateway-service.yaml auth-service.yaml user-service.yaml question-service.yaml exam-service.yaml grading-service.yaml analysis-service.yaml admin-service.yaml; do
  curl -sS -X POST "${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${f}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@docs/nacos/${f}"
done
```

## 4. 运行时环境变量

当 Nacos 地址、账户或中间件连接信息发生变化时，可在启动服务前通过环境变量覆盖。

建议分工如下：

- `.env`：Docker 中间件口令、端口，以及 Nacos Server 认证相关配置
- `.env.runtime`：本机运行各业务服务时覆盖的中间件地址、账号和 `JWT_SECRET`

常用变量包括：

- `NACOS_SERVER_ADDR`
- `NACOS_USERNAME`
- `NACOS_PASSWORD`
- `NACOS_GROUP`
- `NACOS_NAMESPACE`
- `NACOS_AUTH_ENABLE` / `NACOS_AUTH_TOKEN` / `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE`（通常放在 `.env`）
- `MYSQL_HOST` / `MYSQL_PORT`（优先推荐；各服务模板会保留自己的数据库名）
- `MYSQL_URL`（可选；仅在需要一次性覆盖完整 JDBC URL 时使用）
- `MYSQL_USERNAME` / `MYSQL_PASSWORD`
- `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`
- `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`
- `JWT_SECRET`（必填，支持明文或 Base64，解码后至少 32 字节）
- `BOOTSTRAP_DEMO_USERS`（可选，默认 `false`，仅建议本地初始化时临时使用）

`JWT_SECRET` 与密码迁移相关注意事项：

- `JWT_SECRET` 为空时服务会直接启动失败
- `JWT_SECRET` 仍使用历史默认演示密钥时会直接启动失败
- `JWT_SECRET` 解码后不足 32 字节时会直接启动失败
- 如果仍配置 `ALLOW_LEGACY_PLAIN_PASSWORD`，或 `sys_user.password_hash` 中仍存在历史明文密码，`auth-service` 会拒绝启动
- 历史密码迁移请参考 [docs/password-migration.md](../password-migration.md)

Nacos 对外暴露时的建议：

- 保持认证开启
- 不要继续使用默认 `nacos/nacos`
- 仅对受控来源开放 `8848/9848/9849`

## 5. MQ 可靠投递相关配置

`common.yaml` 已内置考试链路使用的 RabbitMQ 可靠投递默认项：

- `spring.rabbitmq.publisher-confirm-type=correlated`
- `spring.rabbitmq.publisher-returns=true`
- `spring.rabbitmq.template.mandatory=true`
- `spring.rabbitmq.listener.simple.acknowledge-mode=manual`
- `smart-exam.mq.exam-submitted.max-retries`
- `smart-exam.mq.exam-submitted.retry-ttl-ms`
- `smart-exam.mq.score-published.max-retries`
- `smart-exam.mq.score-published.retry-ttl-ms`

## 6. 考试调度相关配置

`exam-service.yaml` 中包含考试状态同步和自动交卷调度参数：

- `smart-exam.exam.status-sync-initial-delay-ms`
- `smart-exam.exam.status-sync-interval-ms`
- `smart-exam.exam.auto-force-submit.initial-delay-ms`
- `smart-exam.exam.auto-force-submit.interval-ms`
- `smart-exam.exam.auto-force-submit.batch-size`

## 7. 防作弊规则配置

`exam-service.yaml` 已支持第二批防作弊规则参数配置：

- `smart-exam.exam.anti-cheat.recent-events-limit`
- `smart-exam.exam.anti-cheat.page-default-size`
- `smart-exam.exam.anti-cheat.page-max-size`
- `smart-exam.exam.anti-cheat.max-future-skew-minutes`
- `smart-exam.exam.anti-cheat.repeat-window-minutes`
- `smart-exam.exam.anti-cheat.repeat-penalty-score`
- `smart-exam.exam.anti-cheat.level-step-percent`
- `smart-exam.exam.anti-cheat.medium-threshold`
- `smart-exam.exam.anti-cheat.high-threshold`
- `smart-exam.exam.anti-cheat.critical-threshold`
- `smart-exam.exam.anti-cheat.event-base-scores.*`

## 8. 网关路由说明

`gateway-service.yaml` 中与考试相关的路由包含：

- `/api/v1/exams/**`
- `/api/v1/sessions/**`
- `/api/v1/students/**`（兼容旧学生端路径）

如果学生端还在使用 `/api/v1/students/me/exams`，请保留这条兼容路由。

## 9. 报表功能说明

`analysis-service` 的成绩单接口为：

- `GET /api/v1/reports/exams/{examId}/score-sheet`

这个接口不需要新增专门的 Nacos key，沿用 `common.yaml` 中已有的报表缓存和 MQ 可靠性配置即可。
