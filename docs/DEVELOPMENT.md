# Smart Exam Cloud 开发文档

## 1. 目标

本文档用于本地开发和联调，覆盖以下内容：

- 中间件启动（MySQL、Redis、RabbitMQ、Nacos）
- 数据库初始化
- Nacos 配置导入
- 后端微服务启动
- 前端启动与接口联调
- 常见问题排查

## 2. 项目结构

```text
smart-exam-cloud/
  common/                 # 通用模块（core/web/security）
  services/               # 微服务模块
    gateway-service
    auth-service
    user-service
    question-service
    exam-service
    grading-service
    analysis-service
    admin-service
  smart-exam-web/         # Vue3 + Vite 前端
  docs/
    nacos/                # Nacos 配置模板
    sql/                  # 数据库初始化脚本
  docker-compose.yml      # 本地中间件编排
```

## 3. 环境要求

- JDK 17
- Maven 3.9+
- Node.js 20+
- Docker + Docker Compose

## 4. 启动中间件

在项目根目录执行：

```bash
docker compose up -d
```

当前会启动：

- MySQL: `3306`
- Redis: `6379`
- RabbitMQ: `5672`（管理台 `15672`）
- Nacos: `8848`（同时暴露 `9848`、`9849`）

检查状态：

```bash
docker compose ps
```

## 5. 初始化数据库

注意：当前 `docker-compose.yml` 没有挂载 `./docs/sql` 到 MySQL 初始化目录，因此需要手动执行 SQL。

### 5.1 Windows PowerShell

```powershell
Get-Content docs/sql/01_init_databases.sql | docker exec -i smart-exam-mysql mysql -uroot -proot
Get-Content docs/sql/02_core_tables.sql | docker exec -i smart-exam-mysql mysql -uroot -proot
Get-Content docs/sql/03_seed_java_questions_100.sql | docker exec -i smart-exam-mysql mysql -uroot -proot
Get-Content docs/sql/04_seed_users_120.sql | docker exec -i smart-exam-mysql mysql -uroot -proot
Get-Content docs/sql/05_seed_teacher_question_banks_20x200.sql | docker exec -i smart-exam-mysql mysql -uroot -proot
```

### 5.2 Linux/macOS

```bash
docker exec -i smart-exam-mysql mysql -uroot -proot < docs/sql/01_init_databases.sql
docker exec -i smart-exam-mysql mysql -uroot -proot < docs/sql/02_core_tables.sql
docker exec -i smart-exam-mysql mysql -uroot -proot < docs/sql/03_seed_java_questions_100.sql
docker exec -i smart-exam-mysql mysql -uroot -proot < docs/sql/04_seed_users_120.sql
docker exec -i smart-exam-mysql mysql -uroot -proot < docs/sql/05_seed_teacher_question_banks_20x200.sql
```

`03_seed_java_questions_100.sql` 会向 `question_db.q_question` 追加 100 道 Java 题（`SINGLE/MULTI/JUDGE/FILL/SHORT` 各 20 题）。
`04_seed_users_120.sql` 会补齐 `teacher001~teacher020` 与 `student001~student100`。
`05_seed_teacher_question_banks_20x200.sql` 会为 20 位教师各生成 200 题独立题库（共 4000 题）。

### 5.3 默认测试账号

- `02_core_tables.sql` 会初始化 `admin`、`teacher001`、`student001`，默认密码均为 `123456`。
- `04_seed_users_120.sql` 会继续补齐 `teacher001~teacher020` 与 `student001~student100`。
- 若未导入 `04_seed_users_120.sql`，`auth-service` 首次登录时也会自动补齐 `admin`、`teacher001`、`student001`。

## 6. 初始化 Nacos 配置

服务会从 Nacos 加载：

- `common.yaml`
- `${spring.application.name}.yaml`

配置模板位于 `docs/nacos/`，需要导入这 9 个 Data ID：

- `common.yaml`
- `gateway-service.yaml`
- `auth-service.yaml`
- `user-service.yaml`
- `question-service.yaml`
- `exam-service.yaml`
- `grading-service.yaml`
- `analysis-service.yaml`
- `admin-service.yaml`

详细步骤见：`docs/nacos/README.md`

其中 `exam-service.yaml` 包含考试状态流转与超时自动交卷调度参数：

- `smart-exam.exam.status-sync-initial-delay-ms`
- `smart-exam.exam.status-sync-interval-ms`
- `smart-exam.exam.auto-force-submit.initial-delay-ms`
- `smart-exam.exam.auto-force-submit.interval-ms`
- `smart-exam.exam.auto-force-submit.batch-size`

## 7. 本地地址建议

`application.yml` 和 `docs/nacos/*.yaml` 默认使用 `192.168.242.10`。  
如果你在本机开发并使用本地 Docker，建议改为 `127.0.0.1` 或 `localhost`。

至少要保证这些地址可达：

- `NACOS_SERVER_ADDR` -> `127.0.0.1:8848`
- MySQL 地址 -> `127.0.0.1:3306`
- Redis 地址 -> `127.0.0.1:6379`
- RabbitMQ 地址 -> `127.0.0.1:5672`
- `JWT_SECRET` -> 必填（至少 32 字节，支持明文或 Base64）
- `ALLOW_LEGACY_PLAIN_PASSWORD` -> 可选（默认 `false`，仅用于历史明文密码迁移窗口）

密码与密钥安全基线（本地联调建议）：

- `JWT_SECRET` 未设置、仍使用历史默认值、或解码后长度不足 32 字节时，`auth-service/gateway-service` 将在启动阶段失败。
- 管理员重置密码需满足强度要求：8~64 位，且包含大小写字母、数字、特殊字符（不允许空白字符）。

## 8. 启动后端服务

### 8.1 编译

```bash
mvn clean package -DskipTests
```

### 8.2 推荐启动顺序

1. `auth-service` (`9001`)
2. `user-service` (`9100`)
3. `question-service` (`9210`)
4. `exam-service` (`9300`)
5. `grading-service` (`9400`)
6. `analysis-service` (`9500`)
7. `admin-service` (`9600`)
8. `gateway-service` (`9000`)

### 8.3 命令行启动示例

分别在不同终端执行，例如：

```bash
mvn -pl services/auth-service -am spring-boot:run
```

其他服务将模块路径替换为对应目录即可。

## 9. 启动前端

```bash
cd smart-exam-web
npm install
npm run dev
```

默认访问地址：

- 前端: `http://localhost:5173`
- 网关: `http://localhost:9000`

Vite 已配置 `/api` 代理到网关 `9000`。

## 10. 快速联调

### 10.1 登录

```bash
curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"student001\",\"password\":\"123456\"}"
```

### 10.2 带 Token 请求

```bash
curl http://localhost:9000/api/v1/users/me \
  -H "Authorization: Bearer <token>"
```

### 10.3 出试卷与答卷联调（教师出卷，学生答卷）

当前业务规则：

- 题型支持：`SINGLE/MULTI/JUDGE/FILL/SHORT`
- 试卷题型顺序固定：`SINGLE/MULTI -> JUDGE -> FILL -> SHORT`
- 教师负责制定标准答案
- 题库与试卷按教师隔离：教师仅可查看/组卷自己创建的题目和试卷
- 学生只能在考试会话内答卷，`SHORT` 题进入人工评分，其它题型自动判分
- 同一学生同一考试只允许一个会话：进行中可复用，`SUBMITTED/FORCE_SUBMITTED` 不可再次开考
- 考试结束后禁止继续保存答案；提交时间按结束时间封顶，避免超时获益
- 学生可通过 `GET /api/v1/sessions/{sessionId}/answers` 拉取历史答案回填
- 考试结束后若会话仍为 `IN_PROGRESS`，调度会自动转为 `FORCE_SUBMITTED` 并触发判卷
- 交卷与事件发布保持一致：若 `exam.submitted` 事件投递失败，交卷事务回滚并返回错误
- 教师仅可查看自己考试的风险、阅卷任务与分析报表（管理员可全量）

联调建议步骤：

1. 教师创建题目（含标准答案）并按规则组卷。
2. 教师创建考试时必须选择发布学生（`studentIds`）。
3. 学生调用 `GET /api/v1/exams/students/me` 查看“我的考试”列表（兼容旧路径 `/api/v1/students/me/exams`）。
4. 学生对已分配考试调用 `POST /api/v1/exams/{examId}/start` 启动/复用会话。
5. 学生调用 `GET /api/v1/sessions/{sessionId}/paper` 拉取题面（不含标准答案）。
6. 学生调用 `PUT /api/v1/sessions/{sessionId}/answers` 多次保存答案（继续作答前可调用 `GET /api/v1/sessions/{sessionId}/answers` 回填）。
7. 学生调用 `POST /api/v1/sessions/{sessionId}/submit` 主动交卷；若超时未交卷，会话会被调度自动转为 `FORCE_SUBMITTED`。
8. 阅卷服务中 `SHORT` 题任务状态应为 `MANUAL_REQUIRED`，人工评分后变为 `DONE`。
9. 教师调用 `GET /api/v1/reports/exams/{examId}/score-sheet` 查看成绩单。

增量升级 SQL（已有环境）：

```sql
ALTER TABLE exam_db.e_exam_target
ADD UNIQUE INDEX uk_e_exam_target_exam_student(exam_id, student_id);

ALTER TABLE exam_db.e_exam_session
ADD UNIQUE INDEX uk_e_exam_session_exam_user(exam_id, user_id);
```

如果历史环境已存在同一 `(exam_id,student_id)` 或 `(exam_id,user_id)` 多条脏数据，需先清理后再执行索引。

示例清理 SQL（保留每组最大 `id`）：

```sql
DELETE es
FROM exam_db.e_exam_session es
JOIN (
  SELECT exam_id, user_id, MAX(id) AS keep_id
  FROM exam_db.e_exam_session
  GROUP BY exam_id, user_id
  HAVING COUNT(*) > 1
) dup
ON es.exam_id = dup.exam_id
AND es.user_id = dup.user_id
AND es.id <> dup.keep_id;
```

示例（学生读取会话试卷）：

```bash
curl http://localhost:9000/api/v1/sessions/<sessionId>/paper \
  -H "Authorization: Bearer <student-token>"
```

### 10.4 MQ 可靠性联调检查

当前 `exam-service -> grading-service -> analysis-service` 的异步链路已启用以下机制：

- 发布确认：`publisher-confirm-type=correlated` + `publisher-returns=true`
- 消费确认：`acknowledge-mode=manual`
- 失败重试：主队列失败后进入 `retry` 队列，TTL 到期后回流主队列
- 最终死信：超过重试上限后转入 `dlq` 队列

可在 RabbitMQ 管理台（`http://localhost:15672`）重点观察：

- `exam.submitted.q` / `exam.submitted.retry.q` / `exam.submitted.dlq.q`
- `score.published.q` / `score.published.retry.q` / `score.published.dlq.q`

### 10.5 真实判题与正确率联调检查

当前链路已改为真实数据判分：

- `grading-service` 在消费 `exam.submitted` 后，会根据 `exam_db.e_answer` 与题库标准答案计算客观分。
- 客观题：`SINGLE`、`MULTI`、`JUDGE`、`FILL` 自动判分。
- 主观题：`SHORT` 进入 `MANUAL_REQUIRED`，由教师人工评分后发布最终成绩。
- `analysis-service` 会消费 `score.published` 事件中的每题得分快照并落库到 `a_session_question_score`，`question-accuracy-top` 基于真实聚合结果返回。

建议验证步骤：

1. 创建同时包含客观题和 `SHORT` 题的试卷并完成交卷。
2. 在阅卷页确认任务状态为 `MANUAL_REQUIRED`，提交人工评分后变更为 `DONE`。
3. 调用 `GET /api/v1/reports/exams/{examId}/question-accuracy-top?top=10`，确认题目维度返回真实准确率。

### 10.6 考试状态自动流转检查

- `exam-service` 已启用定时调度，按时间窗自动更新状态：`NOT_STARTED -> RUNNING -> FINISHED`。
- 除定时任务外，读取考试详情时也会做一次状态兜底同步，避免缓存状态滞后。
- 会话超时托底已启用：考试结束后仍为 `IN_PROGRESS` 的会话会被自动转为 `FORCE_SUBMITTED` 并触发判卷链路。

### 10.7 管理员中心联调检查

1. 使用管理员账号登录：

```bash
curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"123456\"}"
```

2. 使用管理员 Token 调用概览接口：

```bash
curl http://localhost:9000/api/v1/admin/overview \
  -H "Authorization: Bearer <admin-token>"
```

3. 验证角色权限配置写入（示例：更新 TEACHER 角色权限）：

```bash
curl -X PUT http://localhost:9000/api/v1/admin/roles/TEACHER/permissions \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d "{\"permissionCodes\":[\"ADMIN_OVERVIEW_READ\",\"ADMIN_USER_VIEW\"]}"
```

4. 在 `GET /api/v1/admin/audits` 查询最新审计日志，确认以上操作均有落库记录。

### 10.8 细粒度 RBAC（第二批）联调检查

本批次已将以下服务接口接入 `X-Permissions` 细粒度校验：

- `question-service`：题目创建/列表/详情、试卷创建/详情（含教师题库隔离）
- `analysis-service`：分数分布、题目正确率 TopN 报表
- `user-service`：`/users/me`、`/users/{id}`、`/users`

联调前置条件：

1. 重新执行 `docs/sql/02_core_tables.sql`（包含新增权限码与角色映射）。
2. 重启 `auth-service`、`gateway-service`、`question-service`、`analysis-service`、`user-service`。
3. 重新登录获取新 token（旧 token 可能不含 `permissions` claim）。

验证建议：

1. 学生账号可访问 `/api/v1/users/me`，不可访问 `/api/v1/questions`、`/api/v1/reports/**`、`/api/v1/users`。
2. 教师账号可访问 `/api/v1/questions`、`/api/v1/reports/**`、`/api/v1/users`。
3. 管理员账号可访问上述全部接口（管理员默认放行权限校验，仍受接口角色边界约束）。

示例（教师）：

```bash
curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"teacher001\",\"password\":\"123456\"}"

curl http://localhost:9000/api/v1/questions \
  -H "Authorization: Bearer <teacher-token>"

curl "http://localhost:9000/api/v1/reports/exams/<examId>/score-distribution" \
  -H "Authorization: Bearer <teacher-token>"
```

### 10.9 防作弊事件采集与风险评分（第一批）联调检查

当前已在 `exam-service` 落地第一批能力：

- 学生端上报防作弊事件：`POST /api/v1/sessions/{sessionId}/anti-cheat/events`
- 教师/管理员查看会话风险：`GET /api/v1/sessions/{sessionId}/anti-cheat/risk`（教师仅限本人考试）
- 教师/管理员按考试分页查看风险：`GET /api/v1/exams/{examId}/anti-cheat/risks`（教师仅限本人考试）

联调前置条件：

1. 重新执行 `docs/sql/02_core_tables.sql`（新增 `e_session_risk_event`、`e_session_risk_summary`）。
2. 重启 `auth-service`、`gateway-service`、`exam-service`。
3. 重新登录获取新 token（新增防作弊权限码）。

示例（学生上报）：

```bash
curl -X POST http://localhost:9000/api/v1/sessions/<sessionId>/anti-cheat/events \
  -H "Authorization: Bearer <student-token>" \
  -H "Content-Type: application/json" \
  -d "{\"eventType\":\"SWITCH_SCREEN\",\"metadata\":{\"times\":1}}"
```

示例（教师查看）：

```bash
curl http://localhost:9000/api/v1/sessions/<sessionId>/anti-cheat/risk \
  -H "Authorization: Bearer <teacher-token>"

curl "http://localhost:9000/api/v1/exams/<examId>/anti-cheat/risks?page=1&size=20" \
  -H "Authorization: Bearer <teacher-token>"
```

### 10.10 防作弊规则配置化与后台风险页（第二批）联调检查

`exam-service` 已支持通过 Nacos 配置防作弊规则参数，配置位于：

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

联调步骤：

1. 在 Nacos 更新 `exam-service.yaml` 中上述参数。
2. 重启 `exam-service`（当前按重启生效流程执行）。
3. 在前端进入 `管理后台 -> 风险监控`（`/admin/risks`）查看风险分页和会话事件详情。

### 10.11 成绩单与提交流程可靠性检查

成绩单接口（教师/管理员）：

- `GET /api/v1/reports/exams/{examId}/score-sheet?keyword=<可选>&limit=<可选>`
- `keyword` 支持按 `userId/username/realName` 模糊检索。
- `limit` 默认 `200`，最大 `1000`。

建议验证步骤：

1. 学生提交答卷后，观察 `exam.submitted.q` 进入并被消费。
2. 若任务为 `MANUAL_REQUIRED`，完成人工评分后确认状态改为 `DONE`。
3. 教师调用成绩单接口，确认返回 `records[].rank/userId/username/realName/totalScore/publishedAt`。
4. 同一会话重复发布成绩时，确认成绩单中的发布时间会刷新为最新发布时间。

示例：

```bash
curl "http://localhost:9000/api/v1/reports/exams/<examId>/score-sheet?keyword=student001&limit=50" \
  -H "Authorization: Bearer <teacher-token>"
```

### 10.12 成绩解析开放与学生查分联调检查

当前新增接口：

- 教师/管理员查询开放状态：`GET /api/v1/grading/exams/{examId}/result-release`
- 教师/管理员更新开放状态：`PUT /api/v1/grading/exams/{examId}/result-release`
- 学生/管理员查询会话成绩与解析：`GET /api/v1/grading/sessions/{sessionId}/result`

开放规则：

- 考试结束后自动开放标准答案与解析。
- 考试未结束时，教师可按考试手动提前开放。
- 学生仅可查看本人的会话结果（管理员可全量查看）。

联调前置条件：

1. 重新执行 `docs/sql/02_core_tables.sql`（包含 `g_result_release` 表与 `STUDENT_RESULT_VIEW` 权限）。
2. 重启 `auth-service`、`gateway-service`、`exam-service`、`grading-service`。
3. 重新登录获取新 token（旧 token 不包含新增权限码时会被拒绝）。

建议验证步骤：

1. 学生提交（或等待自动交卷）后，调用 `GET /api/v1/grading/sessions/{sessionId}/result`，确认可读取得分摘要。
2. 在考试未结束时，确认 `detailReleased=false`，标准答案和解析字段不可见。
3. 教师调用发布接口后，学生再次查询结果，确认 `detailReleased=true` 且返回标准答案与解析。

示例（教师发布解析）：

```bash
curl -X PUT http://localhost:9000/api/v1/grading/exams/<examId>/result-release \
  -H "Authorization: Bearer <teacher-token>" \
  -H "Content-Type: application/json" \
  -d "{\"released\":true}"
```

示例（学生查询成绩解析）：

```bash
curl http://localhost:9000/api/v1/grading/sessions/<sessionId>/result \
  -H "Authorization: Bearer <student-token>"
```

## 11. 常见问题

### 11.1 服务启动后连不上 Nacos

- 检查 Nacos 是否启动：`docker compose ps nacos`
- 检查端口是否开放：`8848`、`9848`、`9849`
- 检查 `NACOS_SERVER_ADDR` 是否和当前环境一致

### 11.2 数据库脚本没有自动执行

- 当前 compose 未挂载初始化目录，属于预期行为
- 按第 5 节手动执行 SQL

### 11.3 网关 404 或路由不到下游服务

- 确认下游服务已注册到 Nacos
- 确认 Nacos 中已导入 `gateway-service.yaml`
- 确认访问路径包含 `/api/v1/...`

### 11.4 RabbitMQ 报 PRECONDITION_FAILED（队列参数不一致）

- 通常是你在引入重试/DLQ 前已创建过同名队列（无 DLX 参数）
- 本地可执行 `docker compose down -v` 清理数据卷后重启中间件
- 然后按第 5 节重建数据库，再重新启动服务

### 11.5 analysis-service 报表接口报表不存在 `a_session_question_score`

- 说明你使用的是旧数据库结构（未包含新表）
- 重新执行 `docs/sql/02_core_tables.sql`，或手动创建 `a_session_question_score`

### 11.6 接口返回 `403 Insufficient permission`

- 先确认是否使用了旧 token：重新登录后再试。
- 确认已执行最新 `docs/sql/02_core_tables.sql`，并且 `sys_role_permission` 中存在目标权限码。
- 如果你通过管理员接口改过角色权限，检查是否把必需权限移除了。
- 确认请求经过网关（`gateway-service`）转发；权限头 `X-Permissions` 由网关注入。
- 学生成绩解析接口还需 `STUDENT_RESULT_VIEW`；老师发布解析接口需 `GRADING_TASK_VIEW`。

### 11.7 学生提交后老师看不到阅卷任务或成绩单

- 先检查 `POST /sessions/{sessionId}/submit` 的返回是否成功；若 RabbitMQ 不可用，当前实现会直接返回错误并回滚提交。
- 再检查 RabbitMQ 三组队列是否有堆积：`exam.submitted.*`、`score.published.*`。
- 若 `grading-service` 已有同 `sessionId` 的历史脏任务，当前实现会自动识别并重建；仍异常时可清理该会话任务后重放消息。
- 检查老师是否有 `GRADING_TASK_VIEW`、`REPORT_SCORE_DISTRIBUTION_VIEW` 权限。

### 11.8 服务启动报 `security.jwt.secret` 相关错误

- 先确认已设置 `JWT_SECRET`（明文或 Base64，解码后至少 32 字节）。
- 若提示使用历史默认密钥，请更换为新密钥后重启 `auth-service` 与 `gateway-service`。
- 若在迁移历史明文密码阶段需要临时兼容，可设置 `ALLOW_LEGACY_PLAIN_PASSWORD=true`，迁移完成后应立即恢复为 `false`。

### 11.9 学生已提交但“成绩解析”仍显示未开放

- 先确认会话是否已完成判卷：`taskStatus` 需为 `AUTO_DONE` 或 `DONE`。
- 若考试未结束，属于预期行为；可由老师调用 `PUT /api/v1/grading/exams/{examId}/result-release` 提前开放。
- 若考试已结束仍未开放，检查 `grading-service` 是否正常，及 `g_result_release` 表是否可读写。

## 12. 常用命令

```bash
# 停止中间件（保留数据）
docker compose down

# 停止并删除数据卷（重置本地环境）
docker compose down -v

# 查看某服务日志
docker compose logs -f nacos
docker compose logs -f mysql
```

## 13. 需求与架构文档索引

为避免文档重复维护，本文件只保留开发与联调步骤。
业务需求与技术架构请直接查看以下文档：

- 产品需求（业务流程、模块需求、验收标准）：`docs/PRD.md`
- 技术架构（模块边界、数据架构、事件契约、NFR）：`docs/ARCHITECTURE.md`

建议阅读顺序：

1. 先看 `docs/PRD.md` 明确“做什么”与“验收标准”。
2. 再看 `docs/ARCHITECTURE.md` 明确“怎么实现”和技术约束。
3. 最后按本文件执行本地环境搭建与联调。
