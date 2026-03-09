# smart-exam-cloud

微服务在线考试系统底层代码（Spring Boot + Spring Cloud 风格多模块）。

开发与联调说明请查看：`docs/DEVELOPMENT.md`
产品需求文档请查看：`docs/PRD.md`
技术架构文档请查看：`docs/ARCHITECTURE.md`

## 1. 项目结构

```text
smart-exam-cloud/
  pom.xml
  common/
    common-core/      # 统一响应、错误码、雪花ID、事件模型
    common-web/       # 全局异常处理
    common-security/  # JWT 工具
  services/
    gateway-service/
    auth-service/
    user-service/
    question-service/
    exam-service/
    grading-service/
    analysis-service/
    admin-service/
  docs/
    nacos/
      common.yaml
      *.yaml
    sql/
      01_init_databases.sql
      02_core_tables.sql
      03_seed_java_questions_100.sql
      04_seed_users_120.sql
      05_seed_teacher_question_banks_20x200.sql
  docker-compose.yml
```

## 2. 当前状态（已升级）

所有业务服务已从内存存储升级为 `MyBatis-Plus + MySQL + Redis`。

- 网关 `gateway-service`
  - 路由转发（按 `/api/v1/**`）
  - JWT 全局鉴权过滤器
  - 请求头注入：`X-User-Id`、`X-Role`、`X-Permissions`

- 认证 `auth-service`（`user_db` + Redis）
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/logout`
  - 登录响应与 JWT claim 携带权限码集合（`permissions`）
  - 权限来源优先读取角色权限矩阵（`admin_db.sys_role_permission`），读取失败时按角色默认权限回退
  - 账号来源：`sys_user`
  - 密码策略：默认仅接受 BCrypt 密码；可通过 `ALLOW_LEGACY_PLAIN_PASSWORD=true` 临时开启历史明文兼容迁移窗口
  - JWT 密钥：必须通过 `JWT_SECRET` 注入（明文或 Base64，解码后至少 32 字节）
  - 默认测试账号统一为：`admin`、`teacher001`、`student001`，密码 `123456`
  - 若未导入 `docs/sql/04_seed_users_120.sql`，首次登录时认证服务会自动补齐上述账号

- 用户 `user-service`（`user_db` + Redis）
  - `GET /api/v1/users/me`
  - `GET /api/v1/users/{id}`
  - `GET /api/v1/users`
  - 已接入接口级细粒度 RBAC（角色 + 权限码）

- 题库 `question-service`（`question_db` + Redis）
  - `POST /api/v1/questions`
  - `GET /api/v1/questions`
  - `GET /api/v1/questions/{questionId}`
  - `POST /api/v1/papers`
  - `GET /api/v1/papers`（支持 `keyword/page/size`，用于按名称检索试卷）
  - `GET /api/v1/papers/{paperId}`
  - 出题校验：教师必须提供标准答案；选择题答案需命中选项；判断题标准化为 `true/false`
  - 题库隔离：教师仅可查看/使用自己创建的题目与试卷；管理员可全量查看
  - 组卷校验：题目不可重复，且题型顺序强约束为 `SINGLE/MULTI -> JUDGE -> FILL -> SHORT`
  - 已接入接口级细粒度 RBAC（角色 + 权限码）

- 考试 `exam-service`（`exam_db` + Redis + RabbitMQ）
  - `POST /api/v1/exams`
  - `GET /api/v1/exams/teachers/me`（老师查看自己已发布考试）
  - `GET /api/v1/exams/students/me`（学生查看我的考试，兼容旧路径 `/api/v1/students/me/exams`）
  - `POST /api/v1/exams/{examId}/start`
  - `GET /api/v1/sessions/{sessionId}/paper`（返回会话试卷题面，不含标准答案）
  - `GET /api/v1/sessions/{sessionId}/answers`（读取会话历史作答）
  - `PUT /api/v1/sessions/{sessionId}/answers`
  - `POST /api/v1/sessions/{sessionId}/submit`
  - `POST /api/v1/sessions/{sessionId}/anti-cheat/events`（防作弊事件上报，第一批）
  - `GET /api/v1/sessions/{sessionId}/anti-cheat/risk`（会话风险详情）
  - `GET /api/v1/exams/{examId}/anti-cheat/risks`（按考试查看风险分页）
  - 发布机制：考试创建必须携带 `studentIds`，发布关系落库 `e_exam_target`
  - 答卷校验：仅允许保存当前试卷内题目，且按题型校验答案格式
  - 会话约束：同一学生同一考试仅允许一个会话；已提交/已自动交卷后禁止再次开考
  - 截止约束：考试截止后禁止继续保存答案；提交时间以考试结束时间为上限
  - 超时托底：考试结束后仍为 `IN_PROGRESS` 的会话会被调度自动转为 `FORCE_SUBMITTED` 并触发判卷
  - 提交一致性：交卷与 `exam.submitted` 事件发布在同事务内，MQ 不可用时提交会失败并回滚
  - 权限约束：教师仅可查看自己创建考试的防作弊风险数据
  - 考试状态自动流转：`NOT_STARTED -> RUNNING -> FINISHED`
  - 防作弊第一批：事件采集 + 风险评分聚合（`LOW/MEDIUM/HIGH/CRITICAL`）
  - 防作弊第二批：规则参数支持 `smart-exam.exam.anti-cheat.*` 配置化（Nacos）
  - 已接入接口级细粒度 RBAC（角色 + 权限码）

- 判卷 `grading-service`（`grading_db` + Redis + RabbitMQ）
  - 监听 `exam.submitted.q`
  - 基于考生答案与标准答案进行客观题自动判分（`SINGLE/MULTI/JUDGE/FILL`）
  - 按试卷题目明细生成 `g_question_score`，简答题（`SHORT`）固定进入人工评分
  - `GET /api/v1/grading/tasks`
  - `POST /api/v1/grading/tasks/{taskId}/manual-score`
  - `GET /api/v1/grading/exams/{examId}/result-release`
  - `PUT /api/v1/grading/exams/{examId}/result-release`
  - `GET /api/v1/grading/sessions/{sessionId}/result`（学生查看本人会话成绩与解析）
  - 权限约束：教师仅可查询和评分自己创建考试产生的阅卷任务
  - 成绩解析开放规则：考试结束后自动开放；考试未结束时可由老师手动提前开放
  - 重复/异常恢复：已完成任务会重发成绩事件；检测到不完整任务会清理并重建
  - 发布携带每题得分明细的 `score.published` 事件
  - 已接入接口级细粒度 RBAC（角色 + 权限码）

- 分析 `analysis-service`（`analysis_db` + Redis + RabbitMQ）
  - 监听 `score.published.q`
  - 沉淀会话-题目得分快照（`a_session_question_score`）
  - `GET /api/v1/reports/exams/{examId}/score-distribution`
  - `GET /api/v1/reports/exams/{examId}/score-sheet`（老师查看成绩单，支持 `keyword/limit`）
  - `GET /api/v1/reports/exams/{examId}/question-accuracy-top`（基于真实判分结果聚合）
  - 权限约束：教师仅可查看自己创建考试的统计报表
  - 已接入接口级细粒度 RBAC（角色 + 权限码）

- 管理 `admin-service`（`user_db + admin_db` + Redis）
  - `GET /api/v1/admin/overview`
  - `GET /api/v1/admin/users`
  - `PUT /api/v1/admin/users/{userId}/status`
  - `PUT /api/v1/admin/users/{userId}/role`
  - `PUT /api/v1/admin/users/{userId}/password/reset`
  - `GET /api/v1/admin/roles`
  - `GET /api/v1/admin/permissions`
  - `PUT /api/v1/admin/roles/{roleCode}/permissions`
  - `GET /api/v1/admin/configs`
  - `PUT /api/v1/admin/configs/{configKey}`
  - `GET /api/v1/admin/audits`
  - 密码重置策略：新密码需满足强度要求（8~64 位，包含大小写字母、数字、特殊字符）

## 3. 本地启动

## 3.1 环境要求

- JDK 17（必须）
- Maven 3.9+
- Docker / Docker Compose

## 3.2 启动中间件

```bash
docker compose up -d
```

Nacos 2.x 除了 `8848` 外，还需要暴露 gRPC 端口 `9848`、`9849` 供 Java 客户端连接。
可用以下命令快速确认：

```bash
docker compose ps nacos
```

## 3.3 初始化数据库

注意：当前 `docker-compose.yml` 未挂载 `docs/sql` 到 MySQL 初始化目录，需手动执行 SQL。

- 建议每次 schema 有变更后重跑：
  - `docs/sql/01_init_databases.sql`
  - `docs/sql/02_core_tables.sql`
- 可选压测/演示数据：
  - `docs/sql/03_seed_java_questions_100.sql`
  - `docs/sql/04_seed_users_120.sql`
  - `docs/sql/05_seed_teacher_question_banks_20x200.sql`

`02_core_tables.sql` 默认会初始化 `admin`、`teacher001`、`student001` 三个测试账号。
执行 `04_seed_users_120.sql` 后，会继续补齐 `teacher001~teacher020` 与 `student001~student100`。

## 3.4 初始化 Nacos 配置中心

项目已接入 Nacos 服务发现 + 配置中心。服务启动时会加载：

- `common.yaml`
- `${spring.application.name}.yaml`

请先在 Nacos 中导入 `docs/nacos/` 下模板文件，详情见：

- `docs/nacos/README.md`

默认 Nacos 地址为 `192.168.242.10:8848`，可通过环境变量覆盖：

- `NACOS_SERVER_ADDR`
- `NACOS_USERNAME`
- `NACOS_PASSWORD`
- `NACOS_GROUP`
- `NACOS_NAMESPACE`
- `JWT_SECRET`（必填，至少 32 字节，支持明文或 Base64）
- `ALLOW_LEGACY_PLAIN_PASSWORD`（可选，默认 `false`；仅用于历史明文密码迁移窗口）

未设置 `JWT_SECRET` 或使用不合规密钥时，`auth-service/gateway-service` 会在启动阶段直接失败。

## 3.5 编译

```bash
mvn clean package -DskipTests
```

## 3.6 逐个启动服务

建议顺序：

1. `auth-service`（9001）
2. `user-service`（9100）
3. `question-service`（9210）
4. `exam-service`（9300）
5. `grading-service`（9400）
6. `analysis-service`（9500）
7. `admin-service`（9600）
8. `gateway-service`（9000）

## 4. 快速联调

## 4.1 登录获取 token

```bash
curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"student001\",\"password\":\"123456\"}"
```

## 4.2 带 token 请求

```bash
curl http://localhost:9000/api/v1/users/me \
  -H "Authorization: Bearer <token>"
```

## 5. 说明

- Redis 当前用于防重、幂等与热点缓存。
- RabbitMQ 用于 `exam -> grading -> analysis` 事件链路。
- Nacos 用于服务注册发现与统一配置管理。
- SQL 已包含必要索引与唯一约束（含 `e_exam_target(exam_id,student_id)` 发布去重、`e_exam_session(exam_id,user_id)` 会话唯一、事件落库去重相关约束）。

## 6. 迭代进度（截至 2026-03-09）

已完成：

- 管理员中心（用户治理、角色权限、系统配置、审计日志）。
- MQ 可靠投递与消费失败治理（重试、DLQ）。
- 考试状态自动流转、真实客观判题与题目正确率统计链路。
- 网关之外业务服务的接口级 RBAC（除 `admin-service`）。
- 老师成绩单查询（`/reports/exams/{examId}/score-sheet`）。
- 交卷-判卷链路一致性修复（提交失败回滚、判卷任务自愈）。
- 学生端成绩与解析查看、老师按考试开放解析能力（`/grading/**/result*`）。

部分完成：

- 密码哈希化与密钥安全治理（已支持 BCrypt、默认关闭明文回退并提供迁移开关、JWT 密钥强校验与外置注入；待完成历史账号离线迁移与密钥托管体系化）。
- 防作弊数据采集与规则引擎（事件采集、风险评分聚合与规则参数配置化已落地；完整防作弊能力仍在演进）。

未完成：

- 自动判题规则引擎化（当前为固定题型规则实现）。
- 全链路审计日志。
- 自动化测试体系（API 契约、集成、回归）与契约测试平台化。
- 全链路压测与容量规划。
