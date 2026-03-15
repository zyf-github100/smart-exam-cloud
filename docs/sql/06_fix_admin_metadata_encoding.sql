SET NAMES utf8mb4;

USE admin_db;

START TRANSACTION;

UPDATE sys_role
SET role_name = '平台管理员',
    description = '平台治理、权限分配与运维配置管理'
WHERE role_code = 'ADMIN';

UPDATE sys_role
SET role_name = '教师',
    description = '教学相关业务角色'
WHERE role_code = 'TEACHER';

UPDATE sys_role
SET role_name = '学生',
    description = '考试参与角色'
WHERE role_code = 'STUDENT';

UPDATE sys_permission
SET permission_name = '管理员入口访问',
    description = '访问管理员中心'
WHERE permission_code = 'ADMIN_PLATFORM_ACCESS';

UPDATE sys_permission
SET permission_name = '平台概览读取',
    description = '读取管理员概览指标'
WHERE permission_code = 'ADMIN_OVERVIEW_READ';

UPDATE sys_permission
SET permission_name = '用户列表读取',
    description = '查询平台用户目录与详情'
WHERE permission_code = 'ADMIN_USER_VIEW';

UPDATE sys_permission
SET permission_name = '用户状态变更',
    description = '启用/停用用户'
WHERE permission_code = 'ADMIN_USER_STATUS_UPDATE';

UPDATE sys_permission
SET permission_name = '用户角色变更',
    description = '调整用户业务角色'
WHERE permission_code = 'ADMIN_USER_ROLE_UPDATE';

UPDATE sys_permission
SET permission_name = '用户密码重置',
    description = '重置用户登录密码'
WHERE permission_code = 'ADMIN_USER_PASSWORD_RESET';

UPDATE sys_permission
SET permission_name = '角色权限分配',
    description = '维护角色权限矩阵'
WHERE permission_code = 'ADMIN_ROLE_PERMISSION_ASSIGN';

UPDATE sys_permission
SET permission_name = '系统配置读取',
    description = '读取系统配置项'
WHERE permission_code = 'ADMIN_CONFIG_READ';

UPDATE sys_permission
SET permission_name = '系统配置写入',
    description = '创建或更新系统配置项'
WHERE permission_code = 'ADMIN_CONFIG_WRITE';

UPDATE sys_permission
SET permission_name = '审计日志读取',
    description = '查询管理员审计日志'
WHERE permission_code = 'ADMIN_AUDIT_READ';

UPDATE sys_permission
SET permission_name = '考试创建',
    description = '创建考试并设置时间窗'
WHERE permission_code = 'EXAM_CREATE';

UPDATE sys_permission
SET permission_name = '考试会话开始',
    description = '开始考试会话'
WHERE permission_code = 'EXAM_SESSION_START';

UPDATE sys_permission
SET permission_name = '考试答案保存',
    description = '保存考试会话答案'
WHERE permission_code = 'EXAM_ANSWER_SAVE';

UPDATE sys_permission
SET permission_name = '考试会话提交',
    description = '提交考试会话'
WHERE permission_code = 'EXAM_SESSION_SUBMIT';

UPDATE sys_permission
SET permission_name = '阅卷任务查看',
    description = '查询阅卷任务列表'
WHERE permission_code = 'GRADING_TASK_VIEW';

UPDATE sys_permission
SET permission_name = '人工阅卷评分',
    description = '提交主观题人工评分'
WHERE permission_code = 'GRADING_MANUAL_SCORE';

UPDATE sys_permission
SET permission_name = '题目录入',
    description = '创建题目并落库'
WHERE permission_code = 'QUESTION_CREATE';

UPDATE sys_permission
SET permission_name = '题目列表读取',
    description = '查询题目列表'
WHERE permission_code = 'QUESTION_LIST';

UPDATE sys_permission
SET permission_name = '题目详情读取',
    description = '查询题目详情'
WHERE permission_code = 'QUESTION_DETAIL';

UPDATE sys_permission
SET permission_name = '试卷创建',
    description = '创建试卷并绑定题目'
WHERE permission_code = 'PAPER_CREATE';

UPDATE sys_permission
SET permission_name = '试卷详情读取',
    description = '查询试卷详情'
WHERE permission_code = 'PAPER_DETAIL';

UPDATE sys_permission
SET permission_name = '分数分布报表读取',
    description = '查询考试分数分布报表'
WHERE permission_code = 'REPORT_SCORE_DISTRIBUTION_VIEW';

UPDATE sys_permission
SET permission_name = '题目正确率报表读取',
    description = '查询题目正确率 TopN 报表'
WHERE permission_code = 'REPORT_QUESTION_ACCURACY_VIEW';

UPDATE sys_permission
SET permission_name = '个人资料读取',
    description = '查询当前登录用户资料'
WHERE permission_code = 'USER_SELF_VIEW';

UPDATE sys_permission
SET permission_name = '用户详情读取',
    description = '按用户ID查询资料'
WHERE permission_code = 'USER_PROFILE_VIEW';

UPDATE sys_permission
SET permission_name = '用户列表读取',
    description = '查询用户列表'
WHERE permission_code = 'USER_LIST_VIEW';

UPDATE sys_permission
SET permission_name = '防作弊事件上报',
    description = '上报考试过程防作弊风险事件'
WHERE permission_code = 'EXAM_ANTI_CHEAT_EVENT_REPORT';

UPDATE sys_permission
SET permission_name = '防作弊风险查看',
    description = '查看考试会话风险评分与事件明细'
WHERE permission_code = 'EXAM_ANTI_CHEAT_RISK_VIEW';

UPDATE sys_permission
SET permission_name = '学生成绩解析查看',
    description = '查看本人考试成绩与解析'
WHERE permission_code = 'STUDENT_RESULT_VIEW';

UPDATE sys_config
SET description = '管理员重置密码最小长度策略'
WHERE config_key = 'ADMIN_PASSWORD_RESET_MIN_LENGTH';

UPDATE sys_config
SET description = '审计日志保留天数（建议按归档策略执行）'
WHERE config_key = 'ADMIN_AUDIT_RETENTION_DAYS';

UPDATE sys_config
SET description = '管理员概览缓存时间（秒）'
WHERE config_key = 'ADMIN_OVERVIEW_CACHE_SECONDS';

COMMIT;
