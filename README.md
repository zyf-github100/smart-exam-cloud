# smart-exam-cloud

微服务在线考试系统底层代码（Spring Boot + Spring Cloud 风格多模块）。

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
  docs/
    sql/
      01_init_databases.sql
      02_core_tables.sql
  docker-compose.yml
```

## 2. 当前状态（已升级）

所有业务服务已从内存存储升级为 `MyBatis-Plus + MySQL + Redis`。

- 网关 `gateway-service`
  - 路由转发（按 `/api/v1/**`）
  - JWT 全局鉴权过滤器
  - 请求头注入：`X-User-Id`、`X-Role`

- 认证 `auth-service`（`user_db` + Redis）
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/logout`
  - 账号来源：`sys_user`
  - 首次登录演示账号会自动补齐：`admin/teacher1/stu1`，密码 `123456`

- 用户 `user-service`（`user_db` + Redis）
  - `GET /api/v1/users/me`
  - `GET /api/v1/users/{id}`
  - `GET /api/v1/users`

- 题库 `question-service`（`question_db` + Redis）
  - `POST /api/v1/questions`
  - `GET /api/v1/questions`
  - `GET /api/v1/questions/{questionId}`
  - `POST /api/v1/papers`
  - `GET /api/v1/papers/{paperId}`

- 考试 `exam-service`（`exam_db` + Redis + RabbitMQ）
  - `POST /api/v1/exams`
  - `POST /api/v1/exams/{examId}/start`
  - `PUT /api/v1/sessions/{sessionId}/answers`
  - `POST /api/v1/sessions/{sessionId}/submit`
  - 交卷发布 `exam.submitted` 事件

- 判卷 `grading-service`（`grading_db` + Redis + RabbitMQ）
  - 监听 `exam.submitted.q`
  - 生成判卷任务
  - `GET /api/v1/grading/tasks`
  - `POST /api/v1/grading/tasks/{taskId}/manual-score`
  - 发布 `score.published` 事件

- 分析 `analysis-service`（`analysis_db` + Redis + RabbitMQ）
  - 监听 `score.published.q`
  - `GET /api/v1/reports/exams/{examId}/score-distribution`
  - `GET /api/v1/reports/exams/{examId}/question-accuracy-top`

## 3. 本地启动

## 3.1 环境要求

- JDK 17（必须）
- Maven 3.9+
- Docker / Docker Compose

## 3.2 启动中间件

```bash
docker compose up -d
```

## 3.3 初始化数据库

`docker-compose.yml` 已挂载 `docs/sql` 到 MySQL 初始化目录。

- 首次启动（空数据卷）会自动执行 SQL。
- 若你已经有旧数据卷，初始化脚本不会自动重跑，需要手动执行：
  - `docs/sql/01_init_databases.sql`
  - `docs/sql/02_core_tables.sql`

## 3.4 编译

```bash
mvn clean package -DskipTests
```

## 3.5 逐个启动服务

建议顺序：

1. `auth-service`（9001）
2. `user-service`（9100）
3. `question-service`（9200）
4. `exam-service`（9300）
5. `grading-service`（9400）
6. `analysis-service`（9500）
7. `gateway-service`（9000）

## 4. 快速联调

## 4.1 登录获取 token

```bash
curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"stu1\",\"password\":\"123456\"}"
```

## 4.2 带 token 请求

```bash
curl http://localhost:9000/api/v1/users/me \
  -H "Authorization: Bearer <token>"
```

## 5. 说明

- Redis 当前用于防重、幂等与热点缓存。
- RabbitMQ 用于 `exam -> grading -> analysis` 事件链路。
- SQL 已包含必要索引与唯一约束（如会话唯一、事件落库去重相关约束）。
