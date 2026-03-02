smart-exam-cloud
微服务在线考试系统后端初始代码（Spring Boot + Spring Cloud 风格多模块）。

1. 工程结构
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
  docker-compose.yml
2. 当前已实现
网关 gateway-service
路由转发（按 /api/v1/**）
JWT 全局鉴权过滤器
请求头注入：X-User-Id、X-Role
认证 auth-service
POST /api/v1/auth/login
POST /api/v1/auth/logout
内存账号：admin/teacher1/stu1，密码均为 123456
用户 user-service
GET /api/v1/users/me
GET /api/v1/users/{id}
题库 question-service
POST /api/v1/questions
GET /api/v1/questions
POST /api/v1/papers
GET /api/v1/papers/{paperId}
考试 exam-service
POST /api/v1/exams
POST /api/v1/exams/{examId}/start
PUT /api/v1/sessions/{sessionId}/answers
POST /api/v1/sessions/{sessionId}/submit
交卷发布 exam.submitted 事件
判卷 grading-service
监听 exam.submitted.q
生成阅卷任务
GET /api/v1/grading/tasks
POST /api/v1/grading/tasks/{taskId}/manual-score
发布 score.published 事件
分析 analysis-service
监听 score.published.q
GET /api/v1/reports/exams/{examId}/score-distribution
GET /api/v1/reports/exams/{examId}/question-accuracy-top
3. 本地启动
3.1 启动中间件
docker compose up -d
3.2 编译
mvn clean package -DskipTests
3.3 逐个启动服务
按顺序启动：

auth-service (9001)
user-service (9100)
question-service (9200)
exam-service (9300)
grading-service (9400)
analysis-service (9500)
gateway-service (9000)
4. 快速联调
4.1 登录获取 token
curl -X POST http://localhost:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"stu1\",\"password\":\"123456\"}"
4.2 带 token 请求
curl http://localhost:9000/api/v1/users/me \
  -H "Authorization: Bearer <token>"
5. 说明
当前为“后端初始骨架”，题库、考试、阅卷、统计主要用内存存储演示流程。
下一步可将各服务内存仓储替换为 MyBatis-Plus + MySQL，并接入 Redis 防重和缓存。
