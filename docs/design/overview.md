# 架构说明（当前实现概览）

当前系统已从“可运行骨架”升级为完整可联调版本，核心链路如下：

- 统一入口：`gateway-service`（JWT 鉴权 + `X-User-Id/X-Role/X-Permissions` 透传）
- 业务域：`auth/user/question/exam/grading/analysis/admin`
- 存储层：多 schema MySQL（`user_db/question_db/exam_db/grading_db/analysis_db/admin_db`）+ Redis
- 事件链：`exam.submitted -> score.published`（RabbitMQ 主队列 + 重试队列 + DLQ）

## 当前已落地能力

- 题库与试卷：教师题库隔离、组卷顺序校验、题型标准化校验。
- 考试会话：按学生发布（`e_exam_target`）、单考单会话、历史答案回填、截止时间控制与超时自动交卷。
- 判卷：客观题自动判分、主观题人工评分、成绩解析开放控制、任务异常自愈。
- 报表：分数分布、题目正确率 TopN、老师成绩单（支持 `keyword/limit`）。
- 防作弊：事件上报、会话风险评分、考试维度风险分页。
- 管理域：用户治理、角色权限、系统配置、审计日志。

## 一致性与可靠性要点

- 交卷提交与 `exam.submitted` 发布在同事务内，发布失败会回滚提交。
- 消费端手动 ACK + TTL 重试 + DLQ，支持异常场景可追踪。
- 判卷侧对重复消息与历史半成品任务做恢复处理，避免“有任务但无完整分数”。
- 分析侧按 `sessionId` Upsert 成绩并替换题目得分快照，报表缓存按 `examId` 失效。

## 进一步阅读

- 架构详版：`docs/ARCHITECTURE.md`
- 开发联调：`docs/DEVELOPMENT.md`
- 产品需求：`docs/PRD.md`
