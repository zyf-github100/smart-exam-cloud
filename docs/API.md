# smart-exam-cloud API 鎺ュ彛鏂囨。

> 鏈枃鎸変粨搴撳唴 Controller 涓?Service 婧愮爜鏁寸悊锛屾渶鍚庢牳瀵规棩鏈燂細2026-03-13銆?> 鎺ㄨ崘缁熶竴閫氳繃缃戝叧 `http://localhost:9000` 璁块棶锛涙枃涓矾寰勫潎涓虹綉鍏宠矾寰勩€?
## 1. 閫氱敤绾﹀畾

### 1.1 鏈嶅姟鍏ュ彛

| 鏈嶅姟 | 榛樿绔彛 | 璇存槑 |
| --- | --- | --- |
| gateway-service | `9000` | 鎺ㄨ崘缁熶竴浠庣綉鍏宠闂?|
| auth-service | `9001` | 鐧诲綍銆佺櫥鍑?|
| user-service | `9100` | 鐢ㄦ埛璧勬枡 |
| question-service | `9210` | 棰樺簱銆佽瘯鍗?|
| exam-service | `9300` | 鑰冭瘯銆佷綔绛斻€侀槻浣滃紛 |
| grading-service | `9400` | 鍒ゅ嵎銆佹煡鍒?|
| analysis-service | `9500` | 缁熻鎶ヨ〃 |
| admin-service | `9600` | 绠＄悊鍚庡彴 |

### 1.2 閴存潈涓庤姹傚ご

- `POST /api/v1/auth/login` 涓嶉渶瑕侀壌鏉冦€?- 鍏朵粬鎺ュ彛榛樿閮藉簲閫氳繃缃戝叧鎼哄甫 `Authorization: Bearer <token>` 璁块棶銆?- 缃戝叧浼氬悜涓嬫父涓氬姟鏈嶅姟娉ㄥ叆锛?  - `X-User-Id`
  - `X-Role`
  - `X-Permissions`
- 濡傛灉鐩存帴缁曡繃缃戝叧璋冪敤鍏蜂綋鏈嶅姟锛岄渶瑕佽嚜琛岃ˉ榻愪互涓婂ご锛涘惁鍒欏鏁版帴鍙ｄ細杩斿洖鏈櫥褰曟垨鏃犳潈闄愩€?
### 1.3 缁熶竴鍝嶅簲浣?
鎵€鏈夋帴鍙ｇ粺涓€杩斿洖锛?
```json
{
  "code": 0,
  "message": "OK",
  "data": {}
}
```

鎴愬姛鏃讹細

- `code = 0`
- `message = "OK"`
- `data` 涓轰笟鍔℃暟鎹紝閮ㄥ垎鍐欐帴鍙ｈ繑鍥?`null`

澶辫触鏃讹細

- `code != 0`
- `message` 涓洪敊璇師鍥?- `data = null`

### 1.4 缁熶竴閿欒鐮?
| code | 鍚箟 |
| --- | --- |
| `0` | 鎴愬姛 |
| `40001` | 鍙傛暟閿欒 / 涓氬姟鏍￠獙澶辫触 |
| `40100` | 鏈櫥褰曟垨 Token 鏃犳晥 |
| `40300` | 瑙掕壊鎴栨潈闄愪笉瓒?|
| `40400` | 璧勬簮涓嶅瓨鍦?|
| `40900` | 閲嶅鎻愪氦 / 鐘舵€佸啿绐?|
| `50000` | 绯荤粺鍐呴儴閿欒 |

### 1.5 甯哥敤鏋氫妇

#### 瑙掕壊

- `ADMIN`
- `TEACHER`
- `STUDENT`

#### 棰樺瀷

- `SINGLE`
- `MULTI`
- `JUDGE`
- `FILL`
- `SHORT`

#### 鑰冭瘯鐘舵€?
- `NOT_STARTED`
- `RUNNING`
- `FINISHED`

#### 鑰冭瘯浼氳瘽鐘舵€?
- `IN_PROGRESS`
- `SUBMITTED`
- `FORCE_SUBMITTED`

#### 鍒ゅ嵎浠诲姟鐘舵€?
- `PENDING`
- `AUTO_DONE`
- `MANUAL_REQUIRED`
- `DONE`

#### 闃蹭綔寮婇闄╃瓑绾?
- `LOW`
- `MEDIUM`
- `HIGH`
- `CRITICAL`

### 1.6 鍒嗛〉涓庨檺鍒惰鍒?
- 閫氱敤鍒嗛〉缁撴瀯锛?
```json
{
  "page": 1,
  "size": 20,
  "total": 100,
  "records": []
}
```

- 璇曞嵎鍒楄〃銆佺鐞嗗悗鍙扮敤鎴峰垪琛ㄣ€佸璁″垪琛ㄣ€侀槻浣滃紛椋庨櫓鍒楄〃锛?  - `page` 榛樿 `1`
  - `size` 榛樿 `20`
  - `size` 鏈€澶?`100`
- 鎴愮哗鍗曪細
  - `limit` 榛樿 `200`
  - `limit` 鏈€澶?`1000`
- 棰樼洰姝ｇ‘鐜?Top锛?  - `top` 榛樿 `10`
  - `top` 鏈€澶?`50`

## 2. 鍏叡鏁版嵁缁撴瀯

### 2.1 UserProfile

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 鐢ㄦ埛 ID |
| `username` | `string` | 鐢ㄦ埛鍚?|
| `realName` | `string` | 鐪熷疄濮撳悕 |
| `role` | `string` | 瑙掕壊 |
| `status` | `string` | 鐢ㄦ埛鐘舵€佸瓧绗︿覆 |

### 2.2 QuestionOption

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `key` | `string` | 閫夐」閿紝濡?`A`銆乣B` |
| `text` | `string` | 閫夐」鏂囨 |

### 2.3 Question

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 棰樼洰 ID |
| `type` | `QuestionType` | 棰樺瀷 |
| `stem` | `string` | 棰樺共 |
| `difficulty` | `integer` | 闅惧害锛宍1~5` |
| `knowledgePoint` | `string` | 鐭ヨ瘑鐐?|
| `analysis` | `string` | 棰樼洰瑙ｆ瀽 |
| `answer` | `string` | 鏍囧噯绛旀 |
| `createdBy` | `string` | 鍒涘缓浜?ID |
| `createdAt` | `string(datetime)` | 鍒涘缓鏃堕棿 |
| `options` | `QuestionOption[]` | 閫夐」鍒楄〃锛涢潪閫夋嫨棰樹负绌烘暟缁?|

### 2.4 PaperQuestion

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `questionId` | `string` | 棰樼洰 ID |
| `score` | `integer` | 璇ラ鍒嗗€?|
| `orderNo` | `integer` | 棰樼洰椤哄簭 |

### 2.5 Paper

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 璇曞嵎 ID |
| `name` | `string` | 璇曞嵎鍚嶇О |
| `totalScore` | `integer` | 鎬诲垎 |
| `timeLimitMinutes` | `integer` | 闄愭椂鍒嗛挓鏁?|
| `createdBy` | `string` | 鍒涘缓浜?ID |
| `questions` | `PaperQuestion[]` | 棰樼洰鏄庣粏 |

### 2.6 Exam

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 鑰冭瘯 ID |
| `paperId` | `string` | 璇曞嵎 ID |
| `title` | `string` | 鑰冭瘯鏍囬 |
| `startTime` | `string(datetime)` | 寮€濮嬫椂闂?|
| `endTime` | `string(datetime)` | 缁撴潫鏃堕棿 |
| `antiCheatLevel` | `integer` | 闃蹭綔寮婄瓑绾?|
| `status` | `ExamStatus` | 鑰冭瘯鐘舵€?|
| `createdBy` | `string` | 鍒涘缓浜?ID |
| `targetStudentCount` | `integer` | 鎸囨淳瀛︾敓鏁?|
| `studentIds` | `string[]` | 鎸囨淳瀛︾敓 ID 鍒楄〃锛屼粎鍒涘缓鎺ュ彛杩斿洖 |

### 2.7 AssignedExam

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `examId` | `string` | 鑰冭瘯 ID |
| `paperId` | `string` | 璇曞嵎 ID |
| `title` | `string` | 鑰冭瘯鏍囬 |
| `startTime` | `string(datetime)` | 寮€濮嬫椂闂?|
| `endTime` | `string(datetime)` | 缁撴潫鏃堕棿 |
| `antiCheatLevel` | `integer` | 闃蹭綔寮婄瓑绾?|
| `status` | `ExamStatus` | 鑰冭瘯鐘舵€?|
| `sessionId` | `string` | 宸插瓨鍦ㄧ殑浼氳瘽 ID锛屾病鏈夊垯涓虹┖ |
| `sessionStatus` | `SessionStatus` | 浼氳瘽鐘舵€?|
| `sessionStartTime` | `string(datetime)` | 寮€鑰冩椂闂?|
| `sessionSubmitTime` | `string(datetime)` | 浜ゅ嵎鏃堕棿 |

### 2.8 AnswerItem

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `questionId` | `string` | 棰樼洰 ID |
| `answerContent` | `any` | 浣滅瓟鍐呭锛岀被鍨嬬敱棰樺瀷鍐冲畾 |
| `markedForReview` | `boolean` | 鏄惁鏍囪寰呮鏌?|

### 2.9 SessionAnswer

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `questionId` | `string` | 棰樼洰 ID |
| `answerContent` | `any` | 宸蹭繚瀛樼殑浣滅瓟鍐呭 |
| `markedForReview` | `boolean` | 鏄惁鏍囪寰呮鏌?|
| `updatedAt` | `string(datetime)` | 鏈€杩戞洿鏂版椂闂?|

### 2.10 ExamPaperQuestion

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `questionId` | `string` | 棰樼洰 ID |
| `type` | `string` | 棰樺瀷 |
| `stem` | `string` | 棰樺共 |
| `score` | `integer` | 璇ラ鍒嗗€?|
| `orderNo` | `integer` | 椤哄簭 |
| `options` | `QuestionOption[]` | 浠呴€夋嫨棰樻湁鍊硷紱鍏朵粬棰樺瀷涓虹┖鏁扮粍 |

### 2.11 ExamPaper

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |
| `examId` | `string` | 鑰冭瘯 ID |
| `paperId` | `string` | 璇曞嵎 ID |
| `paperName` | `string` | 璇曞嵎鍚嶇О |
| `totalScore` | `integer` | 鎬诲垎 |
| `timeLimitMinutes` | `integer` | 闄愭椂鍒嗛挓鏁?|
| `questions` | `ExamPaperQuestion[]` | 棰樼洰鍒楄〃锛屼笉鍖呭惈鏍囧噯绛旀涓庤В鏋?|

### 2.12 QuestionScore

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `questionId` | `string` | 棰樼洰 ID |
| `maxScore` | `number` | 婊″垎 |
| `gotScore` | `number` | 寰楀垎 |
| `comment` | `string` | 鍒ゅ嵎澶囨敞锛屽 `AUTO_CORRECT`銆乣PENDING_MANUAL` |
| `objective` | `boolean` | 鏄惁瀹㈣棰?|

### 2.13 GradingTask

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 浠诲姟 ID |
| `examId` | `string` | 鑰冭瘯 ID |
| `sessionId` | `string` | 浼氳瘽 ID |
| `userId` | `string` | 瀛︾敓 ID |
| `status` | `GradingTaskStatus` | 浠诲姟鐘舵€?|
| `objectiveScore` | `number` | 瀹㈣棰樺緱鍒?|
| `subjectiveScore` | `number` | 涓昏棰樺緱鍒?|
| `totalScore` | `number` | 鎬诲垎 |
| `graderId` | `string` | 璇勫垎鏁欏笀 ID |
| `createdAt` | `string(datetime)` | 鍒涘缓鏃堕棿 |
| `updatedAt` | `string(datetime)` | 鏇存柊鏃堕棿 |
| `questionScores` | `QuestionScore[]` | 姣忛寰楀垎 |

### 2.14 AntiCheatRiskSummary

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |
| `examId` | `string` | 鑰冭瘯 ID |
| `userId` | `string` | 瀛︾敓 ID |
| `riskScore` | `integer` | 绱Н椋庨櫓鍒?|
| `riskLevel` | `RiskLevel` | 椋庨櫓绛夌骇 |
| `eventCount` | `integer` | 浜嬩欢鏁伴噺 |
| `lastEventType` | `string` | 鏈€杩戜竴娆′簨浠剁被鍨?|
| `lastEventTime` | `string(datetime)` | 鏈€杩戜竴娆′簨浠舵椂闂?|
| `updatedAt` | `string(datetime)` | 姹囨€绘洿鏂版椂闂?|

### 2.15 AntiCheatRiskEvent

| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 浜嬩欢 ID |
| `eventType` | `string` | 浜嬩欢绫诲瀷 |
| `eventTime` | `string(datetime)` | 浜嬩欢鍙戠敓鏃堕棿 |
| `eventScore` | `integer` | 鏈浜嬩欢椋庨櫓鍒?|
| `metadata` | `object` | 涓婃姤鍏冩暟鎹?|
| `clientIp` | `string` | 瀹㈡埛绔?IP |
| `createdAt` | `string(datetime)` | 鍏ュ簱鏃堕棿 |

## 3. 璁よ瘉鏈嶅姟

### 3.1 `POST /api/v1/auth/login`

- 閴存潈锛氬惁
- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `username` | `string` | 鏄?| 鐢ㄦ埛鍚?|
| `password` | `string` | 鏄?| 瀵嗙爜 |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `token` | `string` | JWT |
| `expiresIn` | `integer` | 杩囨湡绉掓暟 |
| `user.id` | `string` | 鐢ㄦ埛 ID |
| `user.username` | `string` | 鐢ㄦ埛鍚?|
| `user.role` | `string` | 瑙掕壊 |
| `user.permissions` | `string[]` | 鏉冮檺鐮佸垪琛?|

- 璇存槑锛?  - 榛樿婕旂ず璐﹀彿锛歚admin`銆乣teacher001`銆乣student001`锛屽瘑鐮佸潎涓?`123456`銆?  - 鐧诲綍鏃朵細鍋氱煭鏃堕棿闃查噸锛岄噸澶嶈姹傚彲鑳借繑鍥?`40900`銆?  - 鐢ㄦ埛绂佺敤鏃惰繑鍥?`40300`銆?
### 3.2 `POST /api/v1/auth/logout`

- 閴存潈锛氬缓璁甫 Bearer Token
- 璇锋眰浣擄細鏃?- 鎴愬姛鍝嶅簲 `data`锛歚null`
- 璇存槑锛氬綋鍓嶅疄鐜颁粎杩斿洖鎴愬姛锛屼笉鍋氭湇鍔＄ Token 鎷夐粦銆?
## 4. 鐢ㄦ埛鏈嶅姟

### 4.1 `GET /api/v1/users/me`

- 瑙掕壊锛歚ADMIN` / `TEACHER` / `STUDENT`
- 鏉冮檺鐮侊細`USER_SELF_VIEW`
- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 褰撳墠鐢ㄦ埛 ID |
| `role` | `string` | 褰撳墠瑙掕壊 |
| `profile` | `UserProfile` | 鐢ㄦ埛璧勬枡 |

### 4.2 `GET /api/v1/users/{id}`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`USER_PROFILE_VIEW`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 鐢ㄦ埛 ID |

- 鎴愬姛鍝嶅簲 `data`锛歚UserProfile`

### 4.3 `GET /api/v1/users`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`USER_LIST_VIEW`
- 璇锋眰鍙傛暟锛氭棤
- 鎴愬姛鍝嶅簲 `data`锛歚UserProfile[]`

## 5. 棰樺簱鏈嶅姟

### 5.1 `POST /api/v1/questions`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`QUESTION_CREATE`
- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `type` | `QuestionType` | 鏄?| 棰樺瀷 |
| `stem` | `string` | 鏄?| 棰樺共 |
| `options` | `QuestionOption[]` | 鍚?| 閫夋嫨棰樺繀濉紝鍏朵粬棰樺瀷蹇呴』涓虹┖ |
| `answer` | `string` | 鏄?| 鏍囧噯绛旀 |
| `difficulty` | `integer` | 鏄?| `1~5` |
| `knowledgePoint` | `string` | 鍚?| 鐭ヨ瘑鐐?|
| `analysis` | `string` | 鍚?| 棰樼洰瑙ｆ瀽 |

- 鎴愬姛鍝嶅簲 `data`锛歚Question`
- 璇存槑锛?  - `SINGLE` / `MULTI` 蹇呴』鑷冲皯鏈?2 涓€夐」锛宍options.key` 涓嶈兘閲嶅銆?  - `SINGLE` 鐨?`answer` 蹇呴』涓斿彧鑳芥湁 1 涓€夐」閿€?  - `MULTI` 鐨?`answer` 鍙啓鎴?`A,B`銆乣A B` 绛夊垎闅旀牸寮忥紝鏈€缁堜細鍘婚噸骞惰浆澶у啓銆?  - `JUDGE` 鐨?`answer` 鍏佽 `true/false/1/0/yes/no`锛屾渶缁堜細鏍囧噯鍖栦负瀛楃涓?`true` 鎴?`false`銆?  - `FILL` / `SHORT` 涓嶈兘甯?`options`銆?
### 5.2 `GET /api/v1/questions`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`QUESTION_LIST`
- 鎴愬姛鍝嶅簲 `data`锛歚Question[]`
- 璇存槑锛?  - `TEACHER` 浠呰兘鐪嬪埌鑷繁鍒涘缓鐨勯鐩€?  - `ADMIN` 鍙互鐪嬪埌鍏ㄩ儴棰樼洰銆?
### 5.3 `GET /api/v1/questions/{questionId}`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`QUESTION_DETAIL`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `questionId` | `string` | 棰樼洰 ID |

- 鎴愬姛鍝嶅簲 `data`锛歚Question`
- 璇存槑锛氭暀甯堜笉鑳芥煡鐪嬪叾浠栨暀甯堥搴撲腑鐨勯鐩€?
### 5.4 `POST /api/v1/papers`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`PAPER_CREATE`
- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `name` | `string` | 鏄?| 璇曞嵎鍚嶇О |
| `timeLimitMinutes` | `integer` | 鏄?| 闄愭椂鍒嗛挓鏁帮紝`>=1` |
| `questions` | `PaperQuestion[]` | 鏄?| 璇曢鍒楄〃锛屼笉鑳戒负绌?|

- 鎴愬姛鍝嶅簲 `data`锛歚Paper`
- 璇存槑锛?  - 棰樼洰涓嶈兘閲嶅銆?  - `score` 蹇呴』澶т簬 `0`銆?  - `orderNo` 涓嶄紶鏃舵寜鏁扮粍椤哄簭鑷姩琛ヤ负 `1..n`銆?  - `orderNo` 蹇呴』鍞竴涓斿ぇ浜?`0`銆?  - 棰樺瀷椤哄簭寮虹害鏉燂細`SINGLE/MULTI -> JUDGE -> FILL -> SHORT`銆?  - 鏁欏笀鍙兘浣跨敤鑷繁鍒涘缓鐨勯鐩粍鍗枫€?
### 5.5 `GET /api/v1/papers`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`PAPER_DETAIL`
- 鏌ヨ鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `keyword` | `string` | 鍚?| 鎸夎瘯鍗峰悕绉版ā绯婃悳绱?|
| `page` | `integer` | 鍚?| 榛樿 `1` |
| `size` | `integer` | 鍚?| 榛樿 `20`锛屾渶澶?`100` |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `page` | `integer` | 褰撳墠椤?|
| `size` | `integer` | 椤靛ぇ灏?|
| `total` | `integer` | 鎬昏褰曟暟 |
| `records` | `PaperSummary[]` | 璇曞嵎鎽樿鍒楄〃 |

`PaperSummary` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 璇曞嵎 ID |
| `name` | `string` | 璇曞嵎鍚嶇О |
| `totalScore` | `integer` | 鎬诲垎 |
| `timeLimitMinutes` | `integer` | 闄愭椂鍒嗛挓鏁?|
| `createdBy` | `string` | 鍒涘缓浜?ID |
| `createdAt` | `string(datetime)` | 鍒涘缓鏃堕棿 |

### 5.6 `GET /api/v1/papers/{paperId}`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`PAPER_DETAIL`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `paperId` | `string` | 璇曞嵎 ID |

- 鎴愬姛鍝嶅簲 `data`锛歚Paper`
- 璇存槑锛氭暀甯堜笉鑳芥煡鐪嬪叾浠栨暀甯堢殑璇曞嵎銆?
## 6. 鑰冭瘯鏈嶅姟

### 6.1 `POST /api/v1/exams`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`EXAM_CREATE`
- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `paperId` | `string` | 鏄?| 璇曞嵎 ID |
| `title` | `string` | 鏄?| 鑰冭瘯鏍囬 |
| `startTime` | `string(datetime)` | 鏄?| 鏍煎紡 `yyyy-MM-dd HH:mm:ss` |
| `endTime` | `string(datetime)` | 鏄?| 鏍煎紡 `yyyy-MM-dd HH:mm:ss` |
| `antiCheatLevel` | `integer` | 鏄?| 闃蹭綔寮婄瓑绾э紝寤鸿 `0~5` |
| `studentIds` | `string[]` | 鏄?| 鎸囨淳瀛︾敓 ID 鍒楄〃锛屼笉鑳戒负绌?|

- 鎴愬姛鍝嶅簲 `data`锛歚Exam`
- 璇存槑锛?  - `startTime` 蹇呴』鏃╀簬 `endTime`銆?  - 鏁欏笀鍙兘鍙戝竷鑷繁璇曞嵎搴撲腑鐨勮瘯鍗凤紱绠＄悊鍛樺彲浣跨敤浠绘剰璇曞嵎銆?  - `studentIds` 浼氬幓閲嶏紝鏁伴噺涓婇檺 `500`銆?  - `studentIds` 蹇呴』鍏ㄩ儴鏄湁鏁堜笖鍚敤鐨勫鐢熻处鍙枫€?  - 椋庨櫓璁＄畻寮曟搸浼氬皢 `antiCheatLevel` 瀹為檯鎸?`0~5` 鑼冨洿瑁佸壀銆?
### 6.2 `POST /api/v1/exams/{examId}/start`

- 瑙掕壊锛歚ADMIN` / `STUDENT`
- 鏉冮檺鐮侊細`EXAM_SESSION_START`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `examId` | `string` | 鑰冭瘯 ID |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |
| `serverTime` | `string(datetime)` | 鏈嶅姟鍣ㄦ椂闂?|
| `timeLimitSeconds` | `integer` | 鍓╀綑鍙€冭瘯绉掓暟锛涙寜鑰冭瘯缁撴潫鏃堕棿璁＄畻 |

- 璇存槑锛?  - 浠呰€冭瘯澶勪簬杩涜涓椂闂寸獥鏃跺彲寮€鑰冦€?  - 瀛︾敓蹇呴』宸茶鍒嗛厤璇ヨ€冭瘯銆?  - 鍚屼竴瀛︾敓鍚屼竴鑰冭瘯鍙厑璁镐竴涓湁鏁堜細璇濓紱閲嶅璋冪敤浼氬鐢ㄥ凡鏈変細璇濄€?
### 6.3 `GET /api/v1/exams/students/me`

### 6.4 `GET /api/v1/students/me/exams`

- 涓や釜璺緞绛変环锛屽缓璁娇鐢ㄦ柊璺緞 `/api/v1/exams/students/me`
- 瑙掕壊锛歚ADMIN` / `STUDENT`
- 鏉冮檺鐮侊細`EXAM_SESSION_START`
- 鎴愬姛鍝嶅簲 `data`锛歚AssignedExam[]`

### 6.5 `GET /api/v1/exams/teachers/me`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`EXAM_CREATE`
- 鎴愬姛鍝嶅簲 `data`锛歚Exam[]`
- 璇存槑锛?  - `targetStudentCount` 鏈夊€笺€?  - 璇ユ帴鍙ｄ笉杩斿洖 `studentIds` 鏄庣粏銆?
### 6.6 `PUT /api/v1/sessions/{sessionId}/answers`

- 瑙掕壊锛歚ADMIN` / `STUDENT`
- 鏉冮檺鐮侊細`EXAM_ANSWER_SAVE`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |

- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `answers` | `AnswerItem[]` | 鏄?| 绛旀鍒楄〃锛屼笉鑳戒负绌?|

- 璇锋眰绀轰緥锛?
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

- 鎴愬姛鍝嶅簲 `data`锛歚null`
- 璇存槑锛?  - 鍙兘淇濆瓨褰撳墠浼氳瘽鎵€灞炶瘯鍗蜂腑鐨勯鐩€?  - 鍚屼竴璇锋眰鍐?`questionId` 涓嶈兘閲嶅銆?  - 浼氳瘽蹇呴』灞炰簬褰撳墠鐢ㄦ埛锛屼笖浼氳瘽鐘舵€佸繀椤绘槸 `IN_PROGRESS`銆?  - 鑰冭瘯缁撴潫鍚庝笉鑳藉啀淇濆瓨銆?  - `answerContent` 浼氭寜棰樺瀷鏍囧噯鍖栵細
    - `SINGLE`锛氬繀椤诲綊涓€鎴?1 涓€夐」閿紝鏈€缁堝瓨涓哄瓧绗︿覆銆?    - `MULTI`锛氬彲浼犳暟缁勬垨鍒嗛殧瀛楃涓诧紝鏈€缁堝瓨涓哄幓閲嶅悗鐨勫ぇ鍐欐暟缁勩€?    - `JUDGE`锛氬彲浼犲竷灏斿€兼垨 `true/false/1/0/yes/no`銆?    - `FILL` / `SHORT`锛氬彧鑳芥槸鏍囬噺鏂囨湰锛屼笉鑳芥槸鏁扮粍鎴栧璞°€?    - 绌虹瓟妗堜細琚爣鍑嗗寲涓?`""`锛屽閫夌┖绛旀浼氬彉鎴?`[]`銆?
### 6.7 `GET /api/v1/sessions/{sessionId}/paper`

- 瑙掕壊锛歚ADMIN` / `STUDENT`
- 鏉冮檺鐮侊細`EXAM_ANSWER_SAVE`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |

- 鎴愬姛鍝嶅簲 `data`锛歚ExamPaper`
- 璇存槑锛氫粎杩斿洖棰橀潰銆佸垎鍊笺€侀『搴忋€侀€夋嫨棰橀€夐」锛屼笉杩斿洖鏍囧噯绛旀涓庤В鏋愩€?
### 6.8 `GET /api/v1/sessions/{sessionId}/answers`

- 瑙掕壊锛歚ADMIN` / `STUDENT`
- 鏉冮檺鐮侊細`EXAM_ANSWER_SAVE`
- 鎴愬姛鍝嶅簲 `data`锛歚SessionAnswer[]`

### 6.9 `POST /api/v1/sessions/{sessionId}/submit`

- 瑙掕壊锛歚ADMIN` / `STUDENT`
- 鏉冮檺鐮侊細`EXAM_SESSION_SUBMIT`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |
| `status` | `string` | 鎻愪氦鍚庣姸鎬侊紝姝ｅ父涓?`SUBMITTED` |
| `submittedAt` | `string(datetime)` | 瀹為檯璁拌处浜ゅ嵎鏃堕棿 |
| `deadlineExceeded` | `boolean` | 鏄惁宸茶秴鎴鏃堕棿锛涜嫢瓒呮椂鍒?`submittedAt` 浼氳閽冲埗鍒拌€冭瘯缁撴潫鏃堕棿 |

- 璇存槑锛?  - 鍚屼竴浼氳瘽閲嶅浜ゅ嵎杩斿洖 `40900`銆?  - 浜ゅ嵎鎴愬姛鍚庝細鍚屾鍙戝竷 `exam.submitted` 浜嬩欢锛孧Q 涓嶅彲鐢ㄦ椂浜ゅ嵎鏁翠綋鍥炴粴澶辫触銆?
### 6.10 `POST /api/v1/sessions/{sessionId}/anti-cheat/events`

- 瑙掕壊锛歚ADMIN` / `STUDENT`
- 鏉冮檺鐮侊細`EXAM_ANTI_CHEAT_EVENT_REPORT`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |

- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `eventType` | `string` | 鏄?| 浜嬩欢绫诲瀷 |
| `eventTime` | `string(datetime)` | 鍚?| 涓嶄紶鍒欏彇鏈嶅姟鍣ㄥ綋鍓嶆椂闂?|
| `metadata` | `object` | 鍚?| 鎵╁睍鍏冩暟鎹?|

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `accepted` | `boolean` | 鍥哄畾涓?`true` |
| `sessionId` | `string` | 浼氳瘽 ID |
| `eventId` | `string` | 椋庨櫓浜嬩欢 ID |
| `eventType` | `string` | 鏍囧噯鍖栧悗鐨勪簨浠剁被鍨?|
| `eventScore` | `integer` | 鏈浜嬩欢寰楀垎 |
| `riskSummary` | `AntiCheatRiskSummary` | 绱椋庨櫓姒傝 |

- 璇存槑锛?  - 浼氳瘽蹇呴』灞炰簬褰撳墠瀛︾敓涓斾粛涓?`IN_PROGRESS`銆?  - `eventTime` 瓒呭嚭鏈潵瀹瑰繊绐楀彛浼氭姤閿欙紱榛樿鏈€澶氬厑璁稿揩浜庢湇鍔″櫒 `5` 鍒嗛挓锛屽彲閫氳繃 Nacos 閰嶇疆瑕嗙洊銆?  - `eventType` 浼氳鏍囧噯鍖栦负鍏ㄥぇ鍐欎笅鍒掔嚎褰㈠紡锛屽 `switch-screen` -> `SWITCH_SCREEN`銆?  - 榛樿浜嬩欢鍩哄噯鍒嗭細
    - `SWITCH_SCREEN=5`
    - `WINDOW_BLUR=3`
    - `COPY_ATTEMPT=8`
    - `PASTE_ATTEMPT=8`
    - `DEVTOOLS_OPEN=10`
    - `NETWORK_DISCONNECT=6`
    - `MULTI_DEVICE_LOGIN=15`
    - `IDLE_TIMEOUT=4`
    - `OTHER=2`
  - 鍚岀被浜嬩欢鍦ㄩ噸澶嶇獥鍙ｅ唴浼氬彔鍔犻澶栫綒鍒嗭紱椋庨櫓绛夌骇闃堝€间篃鍙€氳繃 Nacos 瑕嗙洊銆?
### 6.11 `GET /api/v1/sessions/{sessionId}/anti-cheat/risk`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`EXAM_ANTI_CHEAT_RISK_VIEW`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `summary` | `AntiCheatRiskSummary` | 椋庨櫓姹囨€?|
| `events` | `AntiCheatRiskEvent[]` | 鏈€杩戜簨浠跺垪琛?|

- 璇存槑锛?  - 鏁欏笀鍙兘鏌ョ湅鑷繁鍒涘缓鑰冭瘯涓嬬殑浼氳瘽椋庨櫓銆?  - `events` 榛樿浠呰繑鍥炴渶杩?`20` 鏉★紝鍙厤缃€?  - 鑻ヤ細璇濇殏鏃犻闄╂暟鎹紝`summary` 浼氳繑鍥炰竴涓粯璁?`LOW/0 鍒?0 娆′簨浠禶 鐨勫崰浣嶅璞°€?
### 6.12 `GET /api/v1/exams/{examId}/anti-cheat/risks`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`EXAM_ANTI_CHEAT_RISK_VIEW`
- 鏌ヨ鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `riskLevel` | `string` | 鍚?| `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` |
| `page` | `integer` | 鍚?| 榛樿 `1` |
| `size` | `integer` | 鍚?| 榛樿 `20`锛屾渶澶?`100` |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `page` | `integer` | 褰撳墠椤?|
| `size` | `integer` | 椤靛ぇ灏?|
| `total` | `integer` | 鎬昏褰曟暟 |
| `records` | `AntiCheatRiskSummary[]` | 椋庨櫓鍒嗛〉缁撴灉 |

## 7. 鍒ゅ嵎鏈嶅姟

### 7.1 `GET /api/v1/grading/tasks`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`GRADING_TASK_VIEW`
- 鏌ヨ鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `status` | `string` | 鍚?| `PENDING` / `AUTO_DONE` / `MANUAL_REQUIRED` / `DONE` |

- 鎴愬姛鍝嶅簲 `data`锛歚GradingTask[]`
- 璇存槑锛?  - 鏁欏笀鍙兘鐪嬪埌鑷繁鑰冭瘯涓嬬殑鍒ゅ嵎浠诲姟銆?  - `questionScores` 浼氶殢浠诲姟涓€骞惰繑鍥炪€?
### 7.2 `POST /api/v1/grading/tasks/{taskId}/manual-score`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`GRADING_MANUAL_SCORE`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `taskId` | `string` | 鍒ゅ嵎浠诲姟 ID |

- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `scores` | `ManualScoreItem[]` | 鏄?| 涓昏棰樿瘎鍒嗘槑缁?|

`ManualScoreItem` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `questionId` | `string` | 鏄?| 棰樼洰 ID |
| `gotScore` | `number` | 鏄?| 寰楀垎 |
| `comment` | `string` | 鍚?| 璇勫垎澶囨敞 |

- 鎴愬姛鍝嶅簲 `data`锛歚GradingTask`
- 璇存槑锛?  - 鍙兘瀵?`MANUAL_REQUIRED` 浠诲姟璇勫垎銆?  - 璇锋眰蹇呴』瑕嗙洊鍏ㄩ儴涓昏棰橈紝涓斾笉鑳藉涔熶笉鑳藉皯銆?  - `gotScore` 蹇呴』鍦?`0 ~ maxScore` 涔嬮棿銆?  - 鎴愬姛鍚庝换鍔＄姸鎬佷細鍙樻垚 `DONE`锛屽苟鍙戝竷鎴愮哗浜嬩欢銆?
### 7.3 `GET /api/v1/grading/exams/{examId}/result-release`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`GRADING_TASK_VIEW`
- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `examId` | `string` | 鑰冭瘯 ID |
| `released` | `boolean` | 鏁欏笀鏄惁鏄惧紡寮€鏀?|
| `releasedAt` | `string(datetime)` | 寮€鏀炬椂闂?|
| `releasedBy` | `string` | 寮€鏀炬搷浣滀汉 |
| `effective` | `boolean` | 鏄惁宸插瀛︾敓鐢熸晥锛沗released=true` 鎴栬€冭瘯宸茬粨鏉熸椂涓?`true` |

### 7.4 `PUT /api/v1/grading/exams/{examId}/result-release`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`GRADING_TASK_VIEW`
- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `released` | `boolean` | 鏄?| 鏄惁寮€鏀炬垚缁╄В鏋?|

- 鎴愬姛鍝嶅簲 `data`锛氫笌 `GET` 鐩稿悓

### 7.5 `GET /api/v1/grading/sessions/{sessionId}/result`

- 瑙掕壊锛歚ADMIN` / `STUDENT`
- 鏉冮檺鐮侊細`STUDENT_RESULT_VIEW`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `sessionId` | `string` | 浼氳瘽 ID |
| `examId` | `string` | 鑰冭瘯 ID |
| `sessionStatus` | `string` | 浼氳瘽鐘舵€?|
| `submittedAt` | `string(datetime)` | 浜ゅ嵎鏃堕棿 |
| `detailReleased` | `boolean` | 鏄惁鍙煡鐪嬫爣鍑嗙瓟妗堜笌瑙ｆ瀽 |
| `detailMessage` | `string` | 鏈紑鏀炬椂鐨勬彁绀烘枃妗?|
| `ready` | `boolean` | 鍒ゅ嵎缁撴灉鏄惁宸插氨缁?|
| `taskStatus` | `string` | 鍒ゅ嵎浠诲姟鐘舵€侊紝娌℃湁浠诲姟鏃朵负 `PENDING` |
| `message` | `string` | `ready=false` 鏃剁殑璇存槑 |
| `summary` | `object` | 鎴愮哗鎽樿 |
| `questions` | `QuestionResult[]` | 姣忛鎴愮哗涓庤В鏋?|

`summary` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `objectiveScore` | `number` | 瀹㈣棰樺緱鍒?|
| `subjectiveScore` | `number` | 涓昏棰樺緱鍒?|
| `totalScore` | `number` | 鎬诲垎 |
| `publishedAt` | `string(datetime)` | 鎴愮哗鍙戝竷鏃堕棿 |

`QuestionResult` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `questionId` | `string` | 棰樼洰 ID |
| `orderNo` | `integer` | 椤哄簭 |
| `type` | `string` | 棰樺瀷 |
| `stem` | `string` | 棰樺共 |
| `analysis` | `string` | 棰樼洰瑙ｆ瀽锛涙湭寮€鏀炬椂涓?`null` |
| `options` | `QuestionOption[]` | 棰樼洰閫夐」 |
| `standardAnswer` | `any` | 鏍囧噯绛旀锛涙湭寮€鏀炬椂涓?`null` |
| `myAnswer` | `any` | 鎴戠殑绛旀 |
| `maxScore` | `number` | 婊″垎 |
| `gotScore` | `number` | 寰楀垎 |
| `objective` | `boolean` | 鏄惁瀹㈣棰?|
| `correct` | `boolean` | 浠呭瑙傞鏈夋槑纭剰涔夛紱婊″垎鍗?`true` |

- 璇存槑锛?  - 鍙湁 `SUBMITTED` / `FORCE_SUBMITTED` 浼氳瘽鍙煡璇€?  - 鑻ユ垚缁╁皻鏈氨缁紝`questions` 涓虹┖鏁扮粍锛宍summary.totalScore` 涓?`null`銆?  - `detailReleased=true` 鐨勬潯浠讹細鑰冭瘯宸茬粨鏉燂紝鎴栬€呮暀甯堝凡鎵嬪姩寮€鏀俱€?  - `standardAnswer` 鐨勮繑鍥炵被鍨嬶細
    - `MULTI` 杩斿洖瀛楃涓叉暟缁?    - `JUDGE` 杩斿洖甯冨皵鍊?    - 鍏朵粬棰樺瀷杩斿洖瀛楃涓?
## 8. 鍒嗘瀽鏈嶅姟

### 8.1 `GET /api/v1/reports/exams/{examId}/score-distribution`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`REPORT_SCORE_DISTRIBUTION_VIEW`
- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `xAxis` | `string[]` | 鍥哄畾鍖洪棿锛歚0-59`銆乣60-69`銆乣70-79`銆乣80-89`銆乣90-100` |
| `series` | `integer[]` | 鍚勫尯闂翠汉鏁帮紝涓?`xAxis` 涓€涓€瀵瑰簲 |

### 8.2 `GET /api/v1/reports/exams/{examId}/question-accuracy-top`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`REPORT_QUESTION_ACCURACY_VIEW`
- 鏌ヨ鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `top` | `integer` | 鍚?| 榛樿 `10`锛屾渶澶?`50` |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `examId` | `string` | 鑰冭瘯 ID |
| `xAxis` | `string[]` | 棰樼洰 ID 鍒楄〃 |
| `series` | `number[]` | 瀵瑰簲姝ｇ‘鐜?|

### 8.3 `GET /api/v1/reports/exams/{examId}/score-sheet`

- 瑙掕壊锛歚ADMIN` / `TEACHER`
- 鏉冮檺鐮侊細`REPORT_SCORE_DISTRIBUTION_VIEW`
- 鏌ヨ鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `keyword` | `string` | 鍚?| 鎸夌敤鎴峰悕 / 鐪熷疄濮撳悕杩囨护 |
| `limit` | `integer` | 鍚?| 榛樿 `200`锛屾渶澶?`1000` |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `examId` | `string` | 鑰冭瘯 ID |
| `keyword` | `string` | 杩囨护鍏抽敭瀛?|
| `limit` | `integer` | 瀹為檯杩斿洖涓婇檺 |
| `total` | `integer` | 褰撳墠杩斿洖璁板綍鏁?|
| `records` | `StudentScoreItem[]` | 鎴愮哗鍗曡褰?|

`StudentScoreItem` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `rank` | `integer` | 鎺掑悕锛屼粠 `1` 寮€濮?|
| `sessionId` | `integer` | 浼氳瘽 ID |
| `userId` | `integer` | 瀛︾敓 ID |
| `username` | `string` | 鐢ㄦ埛鍚?|
| `realName` | `string` | 鐪熷疄濮撳悕 |
| `totalScore` | `number` | 鎬诲垎 |
| `publishedAt` | `string(datetime)` | 鎴愮哗鍙戝竷鏃堕棿 |

## 9. 绠＄悊鏈嶅姟

> 绠＄悊鏈嶅姟鎵€鏈夋帴鍙ｉ兘瑕佹眰 `X-Role=ADMIN`锛屽綋鍓?Controller 鏈澶栨牎楠?`X-Permissions`銆?
### 9.1 `GET /api/v1/admin/overview`

- 瑙掕壊锛歚ADMIN`
- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `totalUsers` | `integer` | 鐢ㄦ埛鎬绘暟 |
| `enabledUsers` | `integer` | 鍚敤鐢ㄦ埛鏁?|
| `disabledUsers` | `integer` | 绂佺敤鐢ㄦ埛鏁?|
| `totalExams` | `integer` | 鑰冭瘯鎬绘暟 |
| `runningExams` | `integer` | 杩涜涓殑鑰冭瘯鏁?|
| `manualRequiredTasks` | `integer` | 寰呬汉宸ュ垽鍗蜂换鍔℃暟 |
| `publishedScores` | `integer` | 宸插彂甯冩垚缁╂暟 |
| `operationsInLast24Hours` | `integer` | 鏈€杩?24 灏忔椂鎿嶄綔鏁?|
| `generatedAt` | `string(datetime)` | 缁熻鐢熸垚鏃堕棿 |

### 9.2 `GET /api/v1/admin/users`

- 瑙掕壊锛歚ADMIN`
- 鏌ヨ鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `keyword` | `string` | 鍚?| 鐢ㄦ埛鍚?/ 鐪熷疄濮撳悕妯＄硦鏌ヨ |
| `roleCode` | `string` | 鍚?| 瑙掕壊浠ｇ爜 |
| `status` | `integer` | 鍚?| `0` 绂佺敤锛宍1` 鍚敤 |
| `page` | `integer` | 鍚?| 榛樿 `1` |
| `size` | `integer` | 鍚?| 榛樿 `20`锛屾渶澶?`100` |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `page` | `integer` | 褰撳墠椤?|
| `size` | `integer` | 椤靛ぇ灏?|
| `total` | `integer` | 鎬昏褰曟暟 |
| `records` | `AdminUserSummary[]` | 鐢ㄦ埛鍒楄〃 |

`AdminUserSummary` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 鐢ㄦ埛 ID |
| `username` | `string` | 鐢ㄦ埛鍚?|
| `realName` | `string` | 鐪熷疄濮撳悕 |
| `role` | `string` | 瑙掕壊浠ｇ爜 |
| `status` | `integer` | `0/1` |
| `statusLabel` | `string` | `ENABLED` / `DISABLED` |
| `createdAt` | `string(datetime)` | 鍒涘缓鏃堕棿 |
| `updatedAt` | `string(datetime)` | 鏇存柊鏃堕棿 |

### 9.3 `PUT /api/v1/admin/users/{userId}/status`

- 瑙掕壊锛歚ADMIN`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `userId` | `string` | 鐢ㄦ埛 ID |

- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `status` | `integer` | 鏄?| `0` 绂佺敤锛宍1` 鍚敤 |
| `reason` | `string` | 鍚?| 鍘熷洜锛屾渶闀?`255` |

- 鎴愬姛鍝嶅簲 `data`锛歚null`

### 9.4 `PUT /api/v1/admin/users/{userId}/role`

- 瑙掕壊锛歚ADMIN`
- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `roleCode` | `string` | 鏄?| 瑙掕壊浠ｇ爜锛屾渶闀?`32` |

- 鎴愬姛鍝嶅簲 `data`锛歚null`

### 9.5 `PUT /api/v1/admin/users/{userId}/password/reset`

- 瑙掕壊锛歚ADMIN`
- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `newPassword` | `string` | 鏄?| `8~64` 浣嶏紝蹇呴』鍚屾椂鍖呭惈澶у皬鍐欏瓧姣嶃€佹暟瀛椼€佺壒娈婂瓧绗︼紝涓斾笉鑳芥湁绌虹櫧 |
| `reason` | `string` | 鍚?| 鍘熷洜锛屾渶闀?`255` |

- 鎴愬姛鍝嶅簲 `data`锛歚null`

### 9.6 `GET /api/v1/admin/roles`

- 瑙掕壊锛歚ADMIN`
- 鎴愬姛鍝嶅簲 `data`锛歚RolePayload[]`

`RolePayload` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 瑙掕壊 ID |
| `roleCode` | `string` | 瑙掕壊浠ｇ爜 |
| `roleName` | `string` | 瑙掕壊鍚嶇О |
| `description` | `string` | 鎻忚堪 |
| `isSystem` | `integer` | 鏄惁绯荤粺瑙掕壊 |
| `status` | `integer` | 瑙掕壊鐘舵€?|
| `permissions` | `PermissionPayload[]` | 瑙掕壊褰撳墠鏉冮檺 |

### 9.7 `GET /api/v1/admin/permissions`

- 瑙掕壊锛歚ADMIN`
- 鎴愬姛鍝嶅簲 `data`锛歚PermissionPayload[]`

`PermissionPayload` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 鏉冮檺 ID |
| `permissionCode` | `string` | 鏉冮檺鐮?|
| `permissionName` | `string` | 鏉冮檺鍚嶇О |
| `moduleKey` | `string` | 妯″潡鏍囪瘑 |
| `description` | `string` | 鎻忚堪 |
| `status` | `integer` | 鐘舵€?|

### 9.8 `PUT /api/v1/admin/roles/{roleCode}/permissions`

- 瑙掕壊锛歚ADMIN`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `roleCode` | `string` | 瑙掕壊浠ｇ爜 |

- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `permissionCodes` | `string[]` | 鏄?| 鏉冮檺鐮佸垪琛紝涓嶈兘涓虹┖ |

- 鎴愬姛鍝嶅簲 `data`锛歚null`
- 璇存槑锛?  - 鎵€鏈夋潈闄愮爜蹇呴』瀛樺湪涓斿浜庡惎鐢ㄧ姸鎬併€?  - 鎻愪氦鐨勬槸鍏ㄩ噺瑕嗙洊锛屼笉鏄閲忚拷鍔犮€?
### 9.9 `GET /api/v1/admin/configs`

- 瑙掕壊锛歚ADMIN`
- 鏌ヨ鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `groupKey` | `string` | 鍚?| 閰嶇疆鍒嗙粍 |
| `keyword` | `string` | 鍚?| 鍖归厤 `configKey` 鎴?`description` |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `total` | `integer` | 璁板綍鎬绘暟 |
| `records` | `ConfigPayload[]` | 閰嶇疆鍒楄〃 |

`ConfigPayload` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `configKey` | `string` | 閰嶇疆閿?|
| `configValue` | `string` | 閰嶇疆鍊?|
| `groupKey` | `string` | 鍒嗙粍 |
| `description` | `string` | 鎻忚堪 |
| `updatedBy` | `string` | 鏈€杩戞洿鏂颁汉 |
| `updatedAt` | `string(datetime)` | 鏈€杩戞洿鏂版椂闂?|

### 9.10 `PUT /api/v1/admin/configs/{configKey}`

- 瑙掕壊锛歚ADMIN`
- 璺緞鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `configKey` | `string` | 閰嶇疆閿紝鏈€缁堜細杞ぇ鍐欙紝鏍煎紡蹇呴』鍖归厤 `[A-Z0-9_.-]{3,128}` |

- 璇锋眰浣擄細

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `configValue` | `string` | 鏄?| 閰嶇疆鍊硷紝鏈€闀?`5000` |
| `groupKey` | `string` | 鍚?| 鍒嗙粍锛屾渶闀?`64`锛屼細杞ぇ鍐?|
| `description` | `string` | 鍚?| 鎻忚堪锛屾渶闀?`255` |

- 鎴愬姛鍝嶅簲 `data`锛歚null`
- 璇存槑锛?  - 涓嶅瓨鍦ㄦ椂鎻掑叆锛屽瓨鍦ㄦ椂鏇存柊銆?  - `groupKey` 涓嶄紶鏃讹細鏂板缓榛樿 `SYSTEM`锛屾洿鏂版部鐢ㄥ師鍒嗙粍銆?
### 9.11 `GET /api/v1/admin/audits`

- 瑙掕壊锛歚ADMIN`
- 鏌ヨ鍙傛暟锛?
| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
| --- | --- | --- | --- |
| `operatorId` | `string` | 鍚?| 鎿嶄綔浜?ID |
| `action` | `string` | 鍚?| 鎿嶄綔绫诲瀷 |
| `targetType` | `string` | 鍚?| 鐩爣绫诲瀷 |
| `startTime` | `string(datetime)` | 鍚?| ISO-8601 鏃堕棿锛屼緥濡?`2026-03-13T09:00:00` |
| `endTime` | `string(datetime)` | 鍚?| ISO-8601 鏃堕棿 |
| `page` | `integer` | 鍚?| 榛樿 `1` |
| `size` | `integer` | 鍚?| 榛樿 `20`锛屾渶澶?`100` |

- 鎴愬姛鍝嶅簲 `data`锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `page` | `integer` | 褰撳墠椤?|
| `size` | `integer` | 椤靛ぇ灏?|
| `total` | `integer` | 鎬昏褰曟暟 |
| `records` | `AuditPayload[]` | 瀹¤鏃ュ織鍒楄〃 |

`AuditPayload` 瀛楁锛?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
| --- | --- | --- |
| `id` | `string` | 瀹¤ ID |
| `operatorId` | `string` | 鎿嶄綔浜?ID |
| `operatorRole` | `string` | 鎿嶄綔浜鸿鑹?|
| `action` | `string` | 鎿嶄綔绫诲瀷 |
| `targetType` | `string` | 鐩爣绫诲瀷 |
| `targetId` | `string` | 鐩爣 ID |
| `detail` | `object` | 璇︽儏 JSON |
| `ip` | `string` | 鏉ユ簮 IP |
| `userAgent` | `string` | UA |
| `createdAt` | `string(datetime)` | 鍒涘缓鏃堕棿 |

## 10. 寤鸿鑱旇皟椤哄簭

1. `POST /api/v1/auth/login` 鑾峰彇 Token銆?2. `GET /api/v1/users/me` 楠岃瘉鐧诲綍涓庣綉鍏虫敞鍏ュご銆?3. 鏁欏笀渚т緷娆¤仈璋冿細棰樺簱 -> 璇曞嵎 -> 鍙戝竷鑰冭瘯銆?4. 瀛︾敓渚т緷娆¤仈璋冿細鎴戠殑鑰冭瘯 -> 寮€鑰?-> 鎷夐 -> 淇濆瓨绛旀 -> 浜ゅ嵎銆?5. 鏁欏笀渚ц仈璋冿細闃呭嵎浠诲姟 -> 浜哄伐璇勫垎 -> 鎶ヨ〃 -> 鎴愮哗寮€鏀俱€?6. 绠＄悊鍛樹晶鑱旇皟锛氱敤鎴枫€佽鑹叉潈闄愩€佺郴缁熼厤缃€佸璁°€?
## 11. 鏂囨。鏉ユ簮

- Controller 璺敱瀹氫箟
- DTO 鏍￠獙娉ㄨВ
- Service 涓殑涓氬姟鏍￠獙銆佸垎椤佃鍒欍€佸搷搴旂粨鏋勬嫾瑁呴€昏緫

濡傞渶杩涗竴姝ヨˉ鍏咃紝鍙互缁х画鍦ㄦ湰鏂囨。涓拷鍔狅細

- 姣忎釜鎺ュ彛鐨?`curl` 绀轰緥
- 鏇村畬鏁寸殑閿欒绀轰緥
- Swagger / OpenAPI 瀹氫箟

