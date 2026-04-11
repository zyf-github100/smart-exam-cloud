# 审计日志扩展说明

## 1. 目标

本次扩展将 `admin-service` 原有的审计写库模式抽成公共能力，并补齐教师/学生关键动作的审计落库。

当前审计写入统一通过 `common-web` 下的公共组件完成：

- `AuditLogService`
- `AuditLogCommand`
- `AuditActions`
- `AuditModules`
- `AuditTargetTypes`
- `AuditHttpUtils`

## 2. 落库表与迁移

初始化建表定义：

- [docs/sql/02_core_tables.sql](sql/02_core_tables.sql)

存量库迁移脚本：

- [docs/sql/07_extend_sys_audit_log.sql](sql/07_extend_sys_audit_log.sql)

审计表统一写入 `admin_db.sys_audit_log`，新增两列用于区分来源：

| 字段 | 类型 | 约定 |
| --- | --- | --- |
| `id` | `BIGINT` | 雪花 ID |
| `service_name` | `VARCHAR(64)` | 写入服务名，默认取 `spring.application.name` |
| `module_key` | `VARCHAR(64)` | 业务模块编码，使用 `AuditModules` 常量 |
| `operator_id` | `BIGINT` | 操作人 ID，无法解析时写 `-1` |
| `operator_role` | `VARCHAR(32)` | 操作角色，建议使用网关注入的 `ADMIN/TEACHER/STUDENT` |
| `action` | `VARCHAR(64)` | 审计事件编码，使用 `AuditActions` 常量 |
| `target_type` | `VARCHAR(64)` | 目标对象类型，使用 `AuditTargetTypes` 常量 |
| `target_id` | `VARCHAR(64)` | 目标对象 ID；无明确对象时写 `-` |
| `detail_json` | `JSON` | 事件详情 JSON |
| `ip` | `VARCHAR(64)` | 客户端 IP，优先取 `X-Forwarded-For` |
| `user_agent` | `VARCHAR(255)` | 请求 UA |
| `created_at` | `DATETIME` | 服务端写入时间 |

## 3. `detail_json` 字段约定

`detail_json` 保持“通用骨架 + 事件专属字段”的约定，避免把所有业务字段都塞进表结构。

通用命名约束：

- 业务主键优先使用 `examId`、`sessionId`、`paperId`、`questionId`、`taskId` 这类显式字段
- 变更前后使用 `beforeXxx` / `afterXxx`
- 数量使用 `xxxCount`
- 分值使用 `xxxScore`
- 时间使用 `xxxAt` 或 `xxxTime`
- 集合字段统一用复数，如 `studentIds`、`questionIds`、`scores`

## 4. 审计事件清单

| 服务 | 模块 | Action | TargetType | 触发动作 | 关键详情字段 |
| --- | --- | --- | --- | --- | --- |
| `admin-service` | `ADMIN` | `USER_STATUS_UPDATED` | `SYS_USER` | 管理员修改用户状态 | `beforeStatus`、`afterStatus`、`reason` |
| `admin-service` | `ADMIN` | `USER_ROLE_UPDATED` | `SYS_USER` | 管理员调整用户角色 | `beforeRole`、`afterRole` |
| `admin-service` | `ADMIN` | `USER_PASSWORD_RESET` | `SYS_USER` | 管理员重置密码 | `username`、`passwordReset`、`reason` |
| `admin-service` | `ADMIN` | `ROLE_PERMISSIONS_UPDATED` | `SYS_ROLE` | 管理员更新角色权限 | `permissionCount`、`permissionCodes` |
| `admin-service` | `ADMIN` | `SYSTEM_CONFIG_UPSERTED` | `SYS_CONFIG` | 管理员更新系统配置 | `groupKey`、`description` |
| `question-service` | `QUESTION` | `QUESTION_CREATED` | `QUESTION` | 教师出题 | `questionType`、`difficulty`、`knowledgePoint`、`optionCount` |
| `question-service` | `PAPER` | `PAPER_CREATED` | `PAPER` | 教师组卷 | `paperName`、`questionCount`、`questionIds`、`totalScore`、`timeLimitMinutes` |
| `exam-service` | `EXAM` | `EXAM_CREATED` | `EXAM` | 教师发布考试 | `title`、`paperId`、`startTime`、`endTime`、`antiCheatLevel`、`targetStudentCount`、`studentIds` |
| `exam-service` | `EXAM_SESSION` | `EXAM_SESSION_STARTED` | `EXAM_SESSION` | 学生开考 | `examId`、`sessionId`、`serverTime`、`timeLimitSeconds` |
| `exam-service` | `EXAM_SESSION` | `EXAM_SESSION_SUBMITTED` | `EXAM_SESSION` | 学生交卷 | `examId`、`sessionId`、`submittedAt`、`deadlineExceeded` |
| `exam-service` | `ANTI_CHEAT` | `ANTI_CHEAT_EVENT_REPORTED` | `ANTI_CHEAT_EVENT` | 学生上报防作弊事件 | `examId`、`sessionId`、`eventType`、`eventTime`、`eventScore`、`riskLevel`、`riskScore`、`eventCount` |
| `grading-service` | `GRADING` | `GRADING_MANUAL_SCORED` | `GRADING_TASK` | 教师人工评分 | `examId`、`sessionId`、`studentId`、`manualQuestionCount`、`subjectiveScore`、`totalScore`、`scores` |
| `grading-service` | `GRADING` | `EXAM_RESULT_RELEASE_UPDATED` | `EXAM` | 教师开放/关闭成绩明细 | `released`、`releasedAt`、`releasedBy`、`effective` |

## 5. 管理端查询约定

`GET /api/v1/admin/audits` 现支持新增的可选筛选参数：

- `serviceName`
- `moduleKey`

返回项也会附带：

- `serviceName`
- `moduleKey`

这样可以在管理员侧区分：

- 哪个服务产生了日志
- 日志属于哪个业务域
- 同一个操作人在不同模块下做了什么动作
