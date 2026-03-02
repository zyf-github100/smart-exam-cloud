# 架构说明（初始版）

当前代码实现的是“可运行骨架”，用于快速进入开发阶段：

- 统一入口：`gateway-service`
- 业务服务：`auth/user/question/exam/grading/analysis`
- 事件链：`exam.submitted -> score.published`
- 统一规范：`ApiResponse`、`ErrorCode`、全局异常处理、雪花 ID、JWT 工具

后续建议优先迭代：

1. 各服务接入 MyBatis-Plus 和独立 schema。
2. exam-service 提交锁改为 Redis `SETNX EX`。
3. grading/analysis 增加幂等表 + DLQ + 重试策略。
4. gateway 接入限流和黑名单策略。

