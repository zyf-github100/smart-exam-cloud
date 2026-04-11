# smart-exam-cloud

基于 Spring Boot / Spring Cloud 风格实现的微服务在线考试系统，覆盖认证鉴权、题库组卷、考试发布、自动判卷、成绩分析和后台管理等核心场景。

- 后端：Spring Boot 3、Spring Cloud Gateway、Spring Cloud Alibaba Nacos、MyBatis-Plus
- 中间件：MySQL、Redis、RabbitMQ、Nacos
- 前端：Vue 3、Vite、Element Plus、ECharts

## 文档导航

- 开发与联调：[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)
- 接口文档：[docs/API.md](docs/API.md)
- 产品需求：[docs/PRD.md](docs/PRD.md)
- 技术架构：[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Nacos 配置导入：[docs/nacos/README.md](docs/nacos/README.md)
- 审计日志说明：[docs/AUDIT_LOGGING.md](docs/AUDIT_LOGGING.md)
- 历史密码迁移：[docs/password-migration.md](docs/password-migration.md)

## 模块结构

```text
smart-exam-cloud/
  pom.xml
  common/
    common-core/      # 错误码、统一响应、雪花 ID、事件模型
    common-web/       # Web 通用能力、全局异常、审计日志组件
    common-security/  # JWT 与安全相关能力
  services/
    gateway-service/  # 网关与统一鉴权
    auth-service/     # 认证与权限装配
    user-service/     # 用户信息
    question-service/ # 题库与试卷
    exam-service/     # 考试、会话、防作弊
    grading-service/  # 自动判卷与人工评分
    analysis-service/ # 成绩统计与报表
    admin-service/    # 管理后台、配置、审计
  smart-exam-web/     # 前端控制台
  docs/               # 开发、架构、接口、SQL、Nacos 模板
  scripts/            # 启动、校验、部署、安全脚本
  docker-compose.yml  # 本地中间件编排
```

## 当前能力

- 统一网关鉴权，支持 JWT、角色与权限码透传
- 基于 MySQL + Redis 的用户、题库、考试、阅卷、分析、管理多库模型
- 教师题库隔离、试卷组装、考试发布与学生会话控制
- 客观题自动判分、简答题人工评分、成绩开放与解析查看
- 成绩单、分数分布、题目正确率 TopN 等分析报表
- 防作弊事件采集、风险评分聚合与规则参数配置化
- 管理员中心支持用户治理、角色权限、系统配置与审计日志
- 提供基础 CI 校验、后端单测与前端 Vitest 测试能力

## 快速开始

### 1. 环境要求

- JDK 17
- Maven 3.9+
- Node.js 20+
- Docker / Docker Compose

### 2. 启动中间件

先准备环境文件：

```bash
cp .env.example .env
cp .env.runtime.example .env.runtime
```

再启动本地中间件：

```bash
docker compose up -d
```

默认会使用：

- MySQL：`3306`
- Redis：`6379`
- RabbitMQ：`5672`
- RabbitMQ 管理台：`15672`
- Nacos：`8848`（同时暴露 `9848`、`9849`）

### 3. 初始化数据库与 Nacos

- 数据库初始化脚本：`docs/sql/`
- Nacos 配置模板：`docs/nacos/`
- 详细导入步骤：见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) 和 [docs/nacos/README.md](docs/nacos/README.md)

### 4. 启动后端服务

推荐顺序：

1. `auth-service`
2. `user-service`
3. `question-service`
4. `exam-service`
5. `grading-service`
6. `analysis-service`
7. `admin-service`
8. `gateway-service`

可直接在项目根目录执行：

```bash
mvn -f services/auth-service/pom.xml -am spring-boot:run
```

也可以使用脚本：

```powershell
.\scripts\dev\run-service.ps1 auth-service
```

服务会优先读取项目根目录的 `.env.runtime` 作为运行时连接配置。

### 5. 启动前端

```bash
cd smart-exam-web
npm install
npm run dev
```

默认访问地址：

- 前端：`http://localhost:5173`
- 网关：`http://localhost:9000`

## 本地联调建议

- 修改中间件地址时，优先改 `.env.runtime`，不要直接改 `docs/nacos/*.yaml` 模板
- `JWT_SECRET` 必须显式配置，且解码后至少 32 字节
- 历史明文密码迁移已收口为离线工具流程，不再推荐通过运行期开关兼容
- 如果需要更完整的排障、联调和验证步骤，请直接看 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)

## 说明

- README 只保留项目入口信息与最短启动路径
- 详细业务规则、接口约束、架构设计和运维步骤统一维护在 `docs/` 下
