# smart-exam-cloud API 接口文档

> 本文按仓库内 Controller 与 Service 源码整理，最后核对日期：2026-03-13。
> 推荐统一通过网关 `http://localhost:9000` 访问；文中路径均为网关路径。

## 1. 通用约定

### 1.1 服务入口

| 服务 | 默认端口 | 说明 |
| --- | --- | --- |
| gateway-service | `9000` | 推荐统一从网关访问 |
| auth-service | `9001` | 登录、登出 |
| user-service | `9100` | 用户资料 |
| question-service | `9210` | 题库、试卷 |
| exam-service | `9300` | 考试、作答、防作弊 |
| grading-service | `9400` | 判卷、查分 |
| analysis-service | `9500` | 统计报表 |
| admin-service | `9600` | 管理后台 |

### 1.2 鉴权与请求头

- `POST /api/v1/auth/login` 不需要鉴权。
- 其他接口默认都应通过网关携带 `Authorization: Bearer <token>` 访问。
- 网关会向下游业务服务注入：
  - `X-User-Id`
  - `X-Role`
  - `X-Permissions`
- 如果直接绕过网关调用具体服务，需要自行补齐以上头；否则多数接口会返回未登录或无权限。

### 1.3 统一响应体

所有接口统一返回：

```json
{
  "code": 0,
  "message": "OK",
  "data": {}
}
```

成功时：

- `code = 0`
- `message = "OK"`
- `data` 为业务数据，部分写接口返回 `null`

失败时：

- `code != 0`
- `message` 为错误原因
- `data = null`

### 1.4 统一错误码

| code | 含义 |
| --- | --- |
| `0` | 成功 |
| `40001` | 参数错误 / 业务校验失败 |
| `40100` | 未登录或 Token 无效 |
| `40300` | 角色或权限不足 |
| `40400` | 资源不存在 |
| `40900` | 重复提交 / 状态冲突 |
| `50000` | 系统内部错误 |

### 1.5 常用枚举

#### 角色

- `ADMIN`
- `TEACHER`
- `STUDENT`

#### 题型

- `SINGLE`
- `MULTI`
- `JUDGE`
- `FILL`
- `SHORT`

#### 考试状态

- `NOT_STARTED`
- `RUNNING`
- `FINISHED`

#### 考试会话状态

- `IN_PROGRESS`
- `SUBMITTED`
- `FORCE_SUBMITTED`

#### 判卷任务状态

- `PENDING`
- `AUTO_DONE`
- `MANUAL_REQUIRED`
- `DONE`

#### 防作弊风险等级

- `LOW`
- `MEDIUM`
- `HIGH`
- `CRITICAL`

### 1.6 分页与限制规则

- 通用分页结构：

```json
{
  "page": 1,
  "size": 20,
  "total": 100,
  "records": []
}
```

- 试卷列表、管理后台用户列表、审计列表、防作弊风险列表：
  - `page` 默认 `1`
  - `size` 默认 `20`
  - `size` 最大 `100`
- 成绩单：
  - `limit` 默认 `200`
  - `limit` 最大 `1000`
- 题目正确率 Top：
  - `top` 默认 `10`
  - `top` 最大 `50`

## 2. 公共数据结构

### 2.1 UserProfile

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 用户 ID |
| `username` | `string` | 用户名 |
| `realName` | `string` | 真实姓名 |
| `role` | `string` | 角色 |
| `status` | `string` | 用户状态字符串 |

### 2.2 QuestionOption

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | `string` | 选项键，如 `A`、`B` |
| `text` | `string` | 选项文案 |

### 2.3 Question

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 题目 ID |
| `type` | `QuestionType` | 题型 |
| `stem` | `string` | 题干 |
| `difficulty` | `integer` | 难度，`1~5` |
| `knowledgePoint` | `string` | 知识点 |
| `analysis` | `string` | 题目解析 |
| `answer` | `string` | 标准答案 |
| `createdBy` | `string` | 创建人 ID |
| `createdAt` | `string(datetime)` | 创建时间 |
| `options` | `QuestionOption[]` | 选项列表；非选择题为空数组 |

### 2.4 PaperQuestion

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `questionId` | `string` | 题目 ID |
| `score` | `integer` | 该题分值 |
| `orderNo` | `integer` | 题目顺序 |

### 2.5 Paper

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 试卷 ID |
| `name` | `string` | 试卷名称 |
| `totalScore` | `integer` | 总分 |
| `timeLimitMinutes` | `integer` | 限时分钟数 |
| `createdBy` | `string` | 创建人 ID |
| `questions` | `PaperQuestion[]` | 题目明细 |

### 2.6 Exam

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 考试 ID |
| `paperId` | `string` | 试卷 ID |
| `title` | `string` | 考试标题 |
| `startTime` | `string(datetime)` | 开始时间 |
| `endTime` | `string(datetime)` | 结束时间 |
| `antiCheatLevel` | `integer` | 防作弊等级 |
| `status` | `ExamStatus` | 考试状态 |
| `createdBy` | `string` | 创建人 ID |
| `targetStudentCount` | `integer` | 指派学生数 |
| `studentIds` | `string[]` | 指派学生 ID 列表，仅创建接口返回 |

### 2.7 AssignedExam

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `examId` | `string` | 考试 ID |
| `paperId` | `string` | 试卷 ID |
| `title` | `string` | 考试标题 |
| `startTime` | `string(datetime)` | 开始时间 |
| `endTime` | `string(datetime)` | 结束时间 |
| `antiCheatLevel` | `integer` | 防作弊等级 |
| `status` | `ExamStatus` | 考试状态 |
| `sessionId` | `string` | 已存在的会话 ID，没有则为空 |
| `sessionStatus` | `SessionStatus` | 会话状态 |
| `sessionStartTime` | `string(datetime)` | 开考时间 |
| `sessionSubmitTime` | `string(datetime)` | 交卷时间 |

### 2.8 AnswerItem

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `questionId` | `string` | 题目 ID |
| `answerContent` | `any` | 作答内容，类型由题型决定 |
| `markedForReview` | `boolean` | 是否标记待检查 |

### 2.9 SessionAnswer

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `questionId` | `string` | 题目 ID |
| `answerContent` | `any` | 已保存的作答内容 |
| `markedForReview` | `boolean` | 是否标记待检查 |
| `updatedAt` | `string(datetime)` | 最近更新时间 |

### 2.10 ExamPaperQuestion

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `questionId` | `string` | 题目 ID |
| `type` | `string` | 题型 |
| `stem` | `string` | 题干 |
| `score` | `integer` | 该题分值 |
| `orderNo` | `integer` | 顺序 |
| `options` | `QuestionOption[]` | 仅选择题有值；其他题型为空数组 |

### 2.11 ExamPaper

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |
| `examId` | `string` | 考试 ID |
| `paperId` | `string` | 试卷 ID |
| `paperName` | `string` | 试卷名称 |
| `totalScore` | `integer` | 总分 |
| `timeLimitMinutes` | `integer` | 限时分钟数 |
| `questions` | `ExamPaperQuestion[]` | 题目列表，不包含标准答案与解析 |

### 2.12 QuestionScore

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `questionId` | `string` | 题目 ID |
| `maxScore` | `number` | 满分 |
| `gotScore` | `number` | 得分 |
| `comment` | `string` | 判卷备注，如 `AUTO_CORRECT`、`PENDING_MANUAL` |
| `objective` | `boolean` | 是否客观题 |

### 2.13 GradingTask

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 任务 ID |
| `examId` | `string` | 考试 ID |
| `sessionId` | `string` | 会话 ID |
| `userId` | `string` | 学生 ID |
| `status` | `GradingTaskStatus` | 任务状态 |
| `objectiveScore` | `number` | 客观题得分 |
| `subjectiveScore` | `number` | 主观题得分 |
| `totalScore` | `number` | 总分 |
| `graderId` | `string` | 评分教师 ID |
| `createdAt` | `string(datetime)` | 创建时间 |
| `updatedAt` | `string(datetime)` | 更新时间 |
| `questionScores` | `QuestionScore[]` | 每题得分 |

### 2.14 AntiCheatRiskSummary

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |
| `examId` | `string` | 考试 ID |
| `userId` | `string` | 学生 ID |
| `riskScore` | `integer` | 累积风险分 |
| `riskLevel` | `RiskLevel` | 风险等级 |
| `eventCount` | `integer` | 事件数量 |
| `lastEventType` | `string` | 最近一次事件类型 |
| `lastEventTime` | `string(datetime)` | 最近一次事件时间 |
| `updatedAt` | `string(datetime)` | 汇总更新时间 |

### 2.15 AntiCheatRiskEvent

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 事件 ID |
| `eventType` | `string` | 事件类型 |
| `eventTime` | `string(datetime)` | 事件发生时间 |
| `eventScore` | `integer` | 本次事件风险分 |
| `metadata` | `object` | 上报元数据 |
| `clientIp` | `string` | 客户端 IP |
| `createdAt` | `string(datetime)` | 入库时间 |

## 3. 认证服务

### 3.1 `POST /api/v1/auth/login`

- 鉴权：否
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `string` | 是 | 用户名 |
| `password` | `string` | 是 | 密码 |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `token` | `string` | JWT |
| `expiresIn` | `integer` | 过期秒数 |
| `user.id` | `string` | 用户 ID |
| `user.username` | `string` | 用户名 |
| `user.role` | `string` | 角色 |
| `user.permissions` | `string[]` | 权限码列表 |

- 说明：
  - 默认演示账号：`admin`、`teacher001`、`student001`，密码均为 `123456`。
  - 登录时会做短时间防重，重复请求可能返回 `40900`。
  - 用户禁用时返回 `40300`。

### 3.2 `POST /api/v1/auth/logout`

- 鉴权：建议带 Bearer Token
- 请求体：无
- 成功响应 `data`：`null`
- 说明：当前实现仅返回成功，不做服务端 Token 拉黑。

## 4. 用户服务

### 4.1 `GET /api/v1/users/me`

- 角色：`ADMIN` / `TEACHER` / `STUDENT`
- 权限码：`USER_SELF_VIEW`
- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 当前用户 ID |
| `role` | `string` | 当前角色 |
| `profile` | `UserProfile` | 用户资料 |

### 4.2 `GET /api/v1/users/{id}`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`USER_PROFILE_VIEW`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 用户 ID |

- 成功响应 `data`：`UserProfile`

### 4.3 `GET /api/v1/users`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`USER_LIST_VIEW`
- 请求参数：无
- 成功响应 `data`：`UserProfile[]`

## 5. 题库服务

### 5.1 `POST /api/v1/questions`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`QUESTION_CREATE`
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | `QuestionType` | 是 | 题型 |
| `stem` | `string` | 是 | 题干 |
| `options` | `QuestionOption[]` | 否 | 选择题必填，其他题型必须为空 |
| `answer` | `string` | 是 | 标准答案 |
| `difficulty` | `integer` | 是 | `1~5` |
| `knowledgePoint` | `string` | 否 | 知识点 |
| `analysis` | `string` | 否 | 题目解析 |

- 成功响应 `data`：`Question`
- 说明：
  - `SINGLE` / `MULTI` 必须至少有 2 个选项，`options.key` 不能重复。
  - `SINGLE` 的 `answer` 必须且只能有 1 个选项键。
  - `MULTI` 的 `answer` 可写成 `A,B`、`A B` 等分隔格式，最终会去重并转大写。
  - `JUDGE` 的 `answer` 允许 `true/false/1/0/yes/no`，最终会标准化为字符串 `true` 或 `false`。
  - `FILL` / `SHORT` 不能带 `options`。

### 5.2 `GET /api/v1/questions`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`QUESTION_LIST`
- 成功响应 `data`：`Question[]`
- 说明：
  - `TEACHER` 仅能看到自己创建的题目。
  - `ADMIN` 可以看到全部题目。

### 5.3 `GET /api/v1/questions/{questionId}`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`QUESTION_DETAIL`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `questionId` | `string` | 题目 ID |

- 成功响应 `data`：`Question`
- 说明：教师不能查看其他教师题库中的题目。

### 5.4 `POST /api/v1/papers`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`PAPER_CREATE`
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | `string` | 是 | 试卷名称 |
| `timeLimitMinutes` | `integer` | 是 | 限时分钟数，`>=1` |
| `questions` | `PaperQuestion[]` | 是 | 试题列表，不能为空 |

- 成功响应 `data`：`Paper`
- 说明：
  - 题目不能重复。
  - `score` 必须大于 `0`。
  - `orderNo` 不传时按数组顺序自动补为 `1..n`。
  - `orderNo` 必须唯一且大于 `0`。
  - 题型顺序强约束：`SINGLE/MULTI -> JUDGE -> FILL -> SHORT`。
  - 教师只能使用自己创建的题目组卷。

### 5.5 `GET /api/v1/papers`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`PAPER_DETAIL`
- 查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | `string` | 否 | 按试卷名称模糊搜索 |
| `page` | `integer` | 否 | 默认 `1` |
| `size` | `integer` | 否 | 默认 `20`，最大 `100` |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `page` | `integer` | 当前页 |
| `size` | `integer` | 页大小 |
| `total` | `integer` | 总记录数 |
| `records` | `PaperSummary[]` | 试卷摘要列表 |

`PaperSummary` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 试卷 ID |
| `name` | `string` | 试卷名称 |
| `totalScore` | `integer` | 总分 |
| `timeLimitMinutes` | `integer` | 限时分钟数 |
| `createdBy` | `string` | 创建人 ID |
| `createdAt` | `string(datetime)` | 创建时间 |

### 5.6 `GET /api/v1/papers/{paperId}`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`PAPER_DETAIL`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `paperId` | `string` | 试卷 ID |

- 成功响应 `data`：`Paper`
- 说明：教师不能查看其他教师的试卷。

## 6. 考试服务

### 6.1 `POST /api/v1/exams`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`EXAM_CREATE`
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paperId` | `string` | 是 | 试卷 ID |
| `title` | `string` | 是 | 考试标题 |
| `startTime` | `string(datetime)` | 是 | 格式 `yyyy-MM-dd HH:mm:ss` |
| `endTime` | `string(datetime)` | 是 | 格式 `yyyy-MM-dd HH:mm:ss` |
| `antiCheatLevel` | `integer` | 是 | 防作弊等级，建议 `0~5` |
| `studentIds` | `string[]` | 是 | 指派学生 ID 列表，不能为空 |

- 成功响应 `data`：`Exam`
- 说明：
  - `startTime` 必须早于 `endTime`。
  - 教师只能发布自己试卷库中的试卷；管理员可使用任意试卷。
  - `studentIds` 会去重，数量上限 `500`。
  - `studentIds` 必须全部是有效且启用的学生账号。
  - 风险计算引擎会将 `antiCheatLevel` 实际按 `0~5` 范围裁剪。

### 6.2 `POST /api/v1/exams/{examId}/start`

- 角色：`ADMIN` / `STUDENT`
- 权限码：`EXAM_SESSION_START`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `examId` | `string` | 考试 ID |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |
| `serverTime` | `string(datetime)` | 服务器时间 |
| `timeLimitSeconds` | `integer` | 剩余可考试秒数；按考试结束时间计算 |

- 说明：
  - 仅考试处于进行中时间窗时可开考。
  - 学生必须已被分配该考试。
  - 同一学生同一考试只允许一个有效会话；重复调用会复用已有会话。

### 6.3 `GET /api/v1/exams/students/me`

### 6.4 `GET /api/v1/students/me/exams`

- 两个路径等价，建议使用新路径 `/api/v1/exams/students/me`
- 角色：`ADMIN` / `STUDENT`
- 权限码：`EXAM_SESSION_START`
- 成功响应 `data`：`AssignedExam[]`

### 6.5 `GET /api/v1/exams/teachers/me`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`EXAM_CREATE`
- 成功响应 `data`：`Exam[]`
- 说明：
  - `targetStudentCount` 有值。
  - 该接口不返回 `studentIds` 明细。

### 6.6 `PUT /api/v1/sessions/{sessionId}/answers`

- 角色：`ADMIN` / `STUDENT`
- 权限码：`EXAM_ANSWER_SAVE`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |

- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `answers` | `AnswerItem[]` | 是 | 答案列表，不能为空 |

- 请求示例：

```json
{
  "answers": [
    { "questionId": "1001", "answerContent": "A", "markedForReview": false },
    { "questionId": "1002", "answerContent": ["A", "C"], "markedForReview": true },
    { "questionId": "1003", "answerContent": true, "markedForReview": false },
    { "questionId": "1004", "answerContent": "Java", "markedForReview": false }
  ]
}
```

- 成功响应 `data`：`null`
- 说明：
  - 只能保存当前会话所属试卷中的题目。
  - 同一请求内 `questionId` 不能重复。
  - 会话必须属于当前用户，且会话状态必须是 `IN_PROGRESS`。
  - 考试结束后不能再保存。
  - `answerContent` 会按题型标准化：
    - `SINGLE`：必须归一成 1 个选项键，最终存为字符串。
    - `MULTI`：可传数组或分隔字符串，最终存为去重后的大写数组。
    - `JUDGE`：可传布尔值或 `true/false/1/0/yes/no`。
    - `FILL` / `SHORT`：只能是标量文本，不能是数组或对象。
    - 空答案会被标准化为 `""`，多选空答案会变成 `[]`。

### 6.7 `GET /api/v1/sessions/{sessionId}/paper`

- 角色：`ADMIN` / `STUDENT`
- 权限码：`EXAM_ANSWER_SAVE`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |

- 成功响应 `data`：`ExamPaper`
- 说明：仅返回题面、分值、顺序、选择题选项，不返回标准答案与解析。

### 6.8 `GET /api/v1/sessions/{sessionId}/answers`

- 角色：`ADMIN` / `STUDENT`
- 权限码：`EXAM_ANSWER_SAVE`
- 成功响应 `data`：`SessionAnswer[]`

### 6.9 `POST /api/v1/sessions/{sessionId}/submit`

- 角色：`ADMIN` / `STUDENT`
- 权限码：`EXAM_SESSION_SUBMIT`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |
| `status` | `string` | 提交后状态，正常为 `SUBMITTED` |
| `submittedAt` | `string(datetime)` | 实际记账交卷时间 |
| `deadlineExceeded` | `boolean` | 是否已超截止时间；若超时则 `submittedAt` 会被钳制到考试结束时间 |

- 说明：
  - 同一会话重复交卷返回 `40900`。
  - 交卷成功后会同步发布 `exam.submitted` 事件，MQ 不可用时交卷整体回滚失败。

### 6.10 `POST /api/v1/sessions/{sessionId}/anti-cheat/events`

- 角色：`ADMIN` / `STUDENT`
- 权限码：`EXAM_ANTI_CHEAT_EVENT_REPORT`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |

- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `eventType` | `string` | 是 | 事件类型 |
| `eventTime` | `string(datetime)` | 否 | 不传则取服务器当前时间 |
| `metadata` | `object` | 否 | 扩展元数据 |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accepted` | `boolean` | 固定为 `true` |
| `sessionId` | `string` | 会话 ID |
| `eventId` | `string` | 风险事件 ID |
| `eventType` | `string` | 标准化后的事件类型 |
| `eventScore` | `integer` | 本次事件得分 |
| `riskSummary` | `AntiCheatRiskSummary` | 累计风险概览 |

- 说明：
  - 会话必须属于当前学生且仍为 `IN_PROGRESS`。
  - `eventTime` 超出未来容忍窗口会报错；默认最多允许快于服务器 `5` 分钟，可通过 Nacos 配置覆盖。
  - `eventType` 会被标准化为全大写下划线形式，如 `switch-screen` -> `SWITCH_SCREEN`。
  - 默认事件基准分：
    - `SWITCH_SCREEN=5`
    - `WINDOW_BLUR=3`
    - `COPY_ATTEMPT=8`
    - `PASTE_ATTEMPT=8`
    - `DEVTOOLS_OPEN=10`
    - `NETWORK_DISCONNECT=6`
    - `MULTI_DEVICE_LOGIN=15`
    - `IDLE_TIMEOUT=4`
    - `OTHER=2`
  - 同类事件在重复窗口内会叠加额外罚分；风险等级阈值也可通过 Nacos 覆盖。

### 6.11 `GET /api/v1/sessions/{sessionId}/anti-cheat/risk`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`EXAM_ANTI_CHEAT_RISK_VIEW`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summary` | `AntiCheatRiskSummary` | 风险汇总 |
| `events` | `AntiCheatRiskEvent[]` | 最近事件列表 |

- 说明：
  - 教师只能查看自己创建考试下的会话风险。
  - `events` 默认仅返回最近 `20` 条，可配置。
  - 若会话暂无风险数据，`summary` 会返回一个默认 `LOW/0 分/0 次事件` 的占位对象。

### 6.12 `GET /api/v1/exams/{examId}/anti-cheat/risks`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`EXAM_ANTI_CHEAT_RISK_VIEW`
- 查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `riskLevel` | `string` | 否 | `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` |
| `page` | `integer` | 否 | 默认 `1` |
| `size` | `integer` | 否 | 默认 `20`，最大 `100` |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `page` | `integer` | 当前页 |
| `size` | `integer` | 页大小 |
| `total` | `integer` | 总记录数 |
| `records` | `AntiCheatRiskSummary[]` | 风险分页结果 |

## 7. 判卷服务

### 7.1 `GET /api/v1/grading/tasks`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`GRADING_TASK_VIEW`
- 查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | `string` | 否 | `PENDING` / `AUTO_DONE` / `MANUAL_REQUIRED` / `DONE` |

- 成功响应 `data`：`GradingTask[]`
- 说明：
  - 教师只能看到自己考试下的判卷任务。
  - `questionScores` 会随任务一并返回。

### 7.2 `POST /api/v1/grading/tasks/{taskId}/manual-score`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`GRADING_MANUAL_SCORE`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | `string` | 判卷任务 ID |

- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `scores` | `ManualScoreItem[]` | 是 | 主观题评分明细 |

`ManualScoreItem` 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `questionId` | `string` | 是 | 题目 ID |
| `gotScore` | `number` | 是 | 得分 |
| `comment` | `string` | 否 | 评分备注 |

- 成功响应 `data`：`GradingTask`
- 说明：
  - 只能对 `MANUAL_REQUIRED` 任务评分。
  - 请求必须覆盖全部主观题，且不能多也不能少。
  - `gotScore` 必须在 `0 ~ maxScore` 之间。
  - 成功后任务状态会变成 `DONE`，并发布成绩事件。

### 7.3 `GET /api/v1/grading/exams/{examId}/result-release`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`GRADING_TASK_VIEW`
- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `examId` | `string` | 考试 ID |
| `released` | `boolean` | 教师是否显式开放 |
| `releasedAt` | `string(datetime)` | 开放时间 |
| `releasedBy` | `string` | 开放操作人 |
| `effective` | `boolean` | 是否已对学生生效；`released=true` 或考试已结束时为 `true` |

### 7.4 `PUT /api/v1/grading/exams/{examId}/result-release`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`GRADING_TASK_VIEW`
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `released` | `boolean` | 是 | 是否开放成绩解析 |

- 成功响应 `data`：与 `GET` 相同

### 7.5 `GET /api/v1/grading/sessions/{sessionId}/result`

- 角色：`ADMIN` / `STUDENT`
- 权限码：`STUDENT_RESULT_VIEW`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 会话 ID |
| `examId` | `string` | 考试 ID |
| `sessionStatus` | `string` | 会话状态 |
| `submittedAt` | `string(datetime)` | 交卷时间 |
| `detailReleased` | `boolean` | 是否可查看标准答案与解析 |
| `detailMessage` | `string` | 未开放时的提示文案 |
| `ready` | `boolean` | 判卷结果是否已就绪 |
| `taskStatus` | `string` | 判卷任务状态，没有任务时为 `PENDING` |
| `message` | `string` | `ready=false` 时的说明 |
| `summary` | `object` | 成绩摘要 |
| `questions` | `QuestionResult[]` | 每题成绩与解析 |

`summary` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `objectiveScore` | `number` | 客观题得分 |
| `subjectiveScore` | `number` | 主观题得分 |
| `totalScore` | `number` | 总分 |
| `publishedAt` | `string(datetime)` | 成绩发布时间 |

`QuestionResult` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `questionId` | `string` | 题目 ID |
| `orderNo` | `integer` | 顺序 |
| `type` | `string` | 题型 |
| `stem` | `string` | 题干 |
| `analysis` | `string` | 题目解析；未开放时为 `null` |
| `options` | `QuestionOption[]` | 题目选项 |
| `standardAnswer` | `any` | 标准答案；未开放时为 `null` |
| `myAnswer` | `any` | 我的答案 |
| `maxScore` | `number` | 满分 |
| `gotScore` | `number` | 得分 |
| `objective` | `boolean` | 是否客观题 |
| `correct` | `boolean` | 仅客观题有明确意义；满分即 `true` |

- 说明：
  - 只有 `SUBMITTED` / `FORCE_SUBMITTED` 会话可查询。
  - 若成绩尚未就绪，`questions` 为空数组，`summary.totalScore` 为 `null`。
  - `detailReleased=true` 的条件：考试已结束，或者教师已手动开放。
  - `standardAnswer` 的返回类型：
    - `MULTI` 返回字符串数组
    - `JUDGE` 返回布尔值
    - 其他题型返回字符串

## 8. 分析服务

### 8.1 `GET /api/v1/reports/exams/{examId}/score-distribution`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`REPORT_SCORE_DISTRIBUTION_VIEW`
- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `xAxis` | `string[]` | 固定区间：`0-59`、`60-69`、`70-79`、`80-89`、`90-100` |
| `series` | `integer[]` | 各区间人数，与 `xAxis` 一一对应 |

### 8.2 `GET /api/v1/reports/exams/{examId}/question-accuracy-top`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`REPORT_QUESTION_ACCURACY_VIEW`
- 查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `top` | `integer` | 否 | 默认 `10`，最大 `50` |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `examId` | `string` | 考试 ID |
| `xAxis` | `string[]` | 题目 ID 列表 |
| `series` | `number[]` | 对应正确率 |

### 8.3 `GET /api/v1/reports/exams/{examId}/score-sheet`

- 角色：`ADMIN` / `TEACHER`
- 权限码：`REPORT_SCORE_DISTRIBUTION_VIEW`
- 查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | `string` | 否 | 按用户名 / 真实姓名过滤 |
| `limit` | `integer` | 否 | 默认 `200`，最大 `1000` |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `examId` | `string` | 考试 ID |
| `keyword` | `string` | 过滤关键字 |
| `limit` | `integer` | 实际返回上限 |
| `total` | `integer` | 当前返回记录数 |
| `records` | `StudentScoreItem[]` | 成绩单记录 |

`StudentScoreItem` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `rank` | `integer` | 排名，从 `1` 开始 |
| `sessionId` | `integer` | 会话 ID |
| `userId` | `integer` | 学生 ID |
| `username` | `string` | 用户名 |
| `realName` | `string` | 真实姓名 |
| `totalScore` | `number` | 总分 |
| `publishedAt` | `string(datetime)` | 成绩发布时间 |

## 9. 管理服务

> 管理服务所有接口都要求 `X-Role=ADMIN`，当前 Controller 未额外校验 `X-Permissions`。

### 9.1 `GET /api/v1/admin/overview`

- 角色：`ADMIN`
- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalUsers` | `integer` | 用户总数 |
| `enabledUsers` | `integer` | 启用用户数 |
| `disabledUsers` | `integer` | 禁用用户数 |
| `totalExams` | `integer` | 考试总数 |
| `runningExams` | `integer` | 进行中的考试数 |
| `manualRequiredTasks` | `integer` | 待人工判卷任务数 |
| `publishedScores` | `integer` | 已发布成绩数 |
| `operationsInLast24Hours` | `integer` | 最近 24 小时操作数 |
| `generatedAt` | `string(datetime)` | 统计生成时间 |

### 9.2 `GET /api/v1/admin/users`

- 角色：`ADMIN`
- 查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | `string` | 否 | 用户名 / 真实姓名模糊查询 |
| `roleCode` | `string` | 否 | 角色代码 |
| `status` | `integer` | 否 | `0` 禁用，`1` 启用 |
| `page` | `integer` | 否 | 默认 `1` |
| `size` | `integer` | 否 | 默认 `20`，最大 `100` |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `page` | `integer` | 当前页 |
| `size` | `integer` | 页大小 |
| `total` | `integer` | 总记录数 |
| `records` | `AdminUserSummary[]` | 用户列表 |

`AdminUserSummary` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 用户 ID |
| `username` | `string` | 用户名 |
| `realName` | `string` | 真实姓名 |
| `role` | `string` | 角色代码 |
| `status` | `integer` | `0/1` |
| `statusLabel` | `string` | `ENABLED` / `DISABLED` |
| `createdAt` | `string(datetime)` | 创建时间 |
| `updatedAt` | `string(datetime)` | 更新时间 |

### 9.3 `PUT /api/v1/admin/users/{userId}/status`

- 角色：`ADMIN`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | `string` | 用户 ID |

- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | `integer` | 是 | `0` 禁用，`1` 启用 |
| `reason` | `string` | 否 | 原因，最长 `255` |

- 成功响应 `data`：`null`

### 9.4 `PUT /api/v1/admin/users/{userId}/role`

- 角色：`ADMIN`
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleCode` | `string` | 是 | 角色代码，最长 `32` |

- 成功响应 `data`：`null`

### 9.5 `PUT /api/v1/admin/users/{userId}/password/reset`

- 角色：`ADMIN`
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `newPassword` | `string` | 是 | `8~64` 位，必须同时包含大小写字母、数字、特殊字符，且不能有空白 |
| `reason` | `string` | 否 | 原因，最长 `255` |

- 成功响应 `data`：`null`

### 9.6 `GET /api/v1/admin/roles`

- 角色：`ADMIN`
- 成功响应 `data`：`RolePayload[]`

`RolePayload` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 角色 ID |
| `roleCode` | `string` | 角色代码 |
| `roleName` | `string` | 角色名称 |
| `description` | `string` | 描述 |
| `isSystem` | `integer` | 是否系统角色 |
| `status` | `integer` | 角色状态 |
| `permissions` | `PermissionPayload[]` | 角色当前权限 |

### 9.7 `GET /api/v1/admin/permissions`

- 角色：`ADMIN`
- 成功响应 `data`：`PermissionPayload[]`

`PermissionPayload` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 权限 ID |
| `permissionCode` | `string` | 权限码 |
| `permissionName` | `string` | 权限名称 |
| `moduleKey` | `string` | 模块标识 |
| `description` | `string` | 描述 |
| `status` | `integer` | 状态 |

### 9.8 `PUT /api/v1/admin/roles/{roleCode}/permissions`

- 角色：`ADMIN`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `roleCode` | `string` | 角色代码 |

- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `permissionCodes` | `string[]` | 是 | 权限码列表，不能为空 |

- 成功响应 `data`：`null`
- 说明：
  - 所有权限码必须存在且处于启用状态。
  - 提交的是全量覆盖，不是增量追加。

### 9.9 `GET /api/v1/admin/configs`

- 角色：`ADMIN`
- 查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `groupKey` | `string` | 否 | 配置分组 |
| `keyword` | `string` | 否 | 匹配 `configKey` 或 `description` |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `integer` | 记录总数 |
| `records` | `ConfigPayload[]` | 配置列表 |

`ConfigPayload` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `configKey` | `string` | 配置键 |
| `configValue` | `string` | 配置值 |
| `groupKey` | `string` | 分组 |
| `description` | `string` | 描述 |
| `updatedBy` | `string` | 最近更新人 |
| `updatedAt` | `string(datetime)` | 最近更新时间 |

### 9.10 `PUT /api/v1/admin/configs/{configKey}`

- 角色：`ADMIN`
- 路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `configKey` | `string` | 配置键，最终会转大写，格式必须匹配 `[A-Z0-9_.-]{3,128}` |

- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `configValue` | `string` | 是 | 配置值，最长 `5000` |
| `groupKey` | `string` | 否 | 分组，最长 `64`，会转大写 |
| `description` | `string` | 否 | 描述，最长 `255` |

- 成功响应 `data`：`null`
- 说明：
  - 不存在时插入，存在时更新。
  - `groupKey` 不传时：新建默认 `SYSTEM`，更新沿用原分组。

### 9.11 `GET /api/v1/admin/audits`

- 角色：`ADMIN`
- 查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `operatorId` | `string` | 否 | 操作人 ID |
| `action` | `string` | 否 | 操作类型 |
| `targetType` | `string` | 否 | 目标类型 |
| `startTime` | `string(datetime)` | 否 | ISO-8601 时间，例如 `2026-03-13T09:00:00` |
| `endTime` | `string(datetime)` | 否 | ISO-8601 时间 |
| `page` | `integer` | 否 | 默认 `1` |
| `size` | `integer` | 否 | 默认 `20`，最大 `100` |

- 成功响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `page` | `integer` | 当前页 |
| `size` | `integer` | 页大小 |
| `total` | `integer` | 总记录数 |
| `records` | `AuditPayload[]` | 审计日志列表 |

`AuditPayload` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 审计 ID |
| `operatorId` | `string` | 操作人 ID |
| `operatorRole` | `string` | 操作人角色 |
| `action` | `string` | 操作类型 |
| `targetType` | `string` | 目标类型 |
| `targetId` | `string` | 目标 ID |
| `detail` | `object` | 详情 JSON |
| `ip` | `string` | 来源 IP |
| `userAgent` | `string` | UA |
| `createdAt` | `string(datetime)` | 创建时间 |

## 10. 建议联调顺序

1. `POST /api/v1/auth/login` 获取 Token。
2. `GET /api/v1/users/me` 验证登录与网关注入头。
3. 教师侧依次联调：题库 -> 试卷 -> 发布考试。
4. 学生侧依次联调：我的考试 -> 开考 -> 拉题 -> 保存答案 -> 交卷。
5. 教师侧联调：阅卷任务 -> 人工评分 -> 报表 -> 成绩开放。
6. 管理员侧联调：用户、角色权限、系统配置、审计。

## 11. 文档来源

- Controller 路由定义
- DTO 校验注解
- Service 中的业务校验、分页规则、响应结构拼装逻辑

如需进一步补充，可以继续在本文档中追加：

- 每个接口的 `curl` 示例
- 更完整的错误示例
- Swagger / OpenAPI 定义
