USE user_db;
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO sys_user (id, username, password_hash, real_name, role, status)
VALUES
    (10001, 'admin', '$2a$10$lLqLKlj9K/MmBkj1BzabmeJqnvSyVROjUC3qgJQMZs3aV4mCdXk3y', 'System Admin', 'ADMIN', 1),
    (20001, 'teacher1', '$2a$10$B6N2RKDLjePeNGCkciuBGOcDiI3p.4Rdu04.RLyo1RmjjhLxLaHqq', 'Teacher One', 'TEACHER', 1),
    (30001, 'stu1', '$2a$10$VNjd.1g8TGSdLOxuQkVNwOu.RaPcQQHEZV97tO/rF9pVm8Xuz2KD2', 'Student One', 'STUDENT', 1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    real_name = VALUES(real_name),
    role = VALUES(role),
    status = VALUES(status);

USE admin_db;
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    is_system TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sys_role_status(status)
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY,
    permission_code VARCHAR(64) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    module_key VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sys_permission_module_status(module_key, status)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_permission(role_code, permission_code),
    INDEX idx_sys_role_permission_role(role_code),
    INDEX idx_sys_role_permission_permission(permission_code)
);

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGINT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    operator_role VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64),
    detail_json JSON NULL,
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sys_audit_log_operator_created(operator_id, created_at),
    INDEX idx_sys_audit_log_action_created(action, created_at),
    INDEX idx_sys_audit_log_created(created_at)
);

CREATE TABLE IF NOT EXISTS sys_config (
    config_key VARCHAR(128) PRIMARY KEY,
    config_value TEXT NOT NULL,
    group_key VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    description VARCHAR(255),
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sys_config_group_key(group_key)
);

INSERT INTO sys_role (id, role_code, role_name, description, is_system, status)
VALUES
    (91001, 'ADMIN', '平台管理员', '平台治理、权限分配与运维配置管理', 1, 1),
    (91002, 'TEACHER', '教师', '教学相关业务角色', 1, 1),
    (91003, 'STUDENT', '学生', '考试参与角色', 1, 1)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    description = VALUES(description),
    is_system = VALUES(is_system),
    status = VALUES(status);

INSERT INTO sys_permission (id, permission_code, permission_name, module_key, description, status)
VALUES
    (92001, 'ADMIN_PLATFORM_ACCESS', '管理员入口访问', 'ADMIN', '访问管理员中心', 1),
    (92002, 'ADMIN_OVERVIEW_READ', '平台概览读取', 'ADMIN', '读取管理员概览指标', 1),
    (92003, 'ADMIN_USER_VIEW', '用户列表读取', 'ADMIN', '查询平台用户目录与详情', 1),
    (92004, 'ADMIN_USER_STATUS_UPDATE', '用户状态变更', 'ADMIN', '启用/停用用户', 1),
    (92005, 'ADMIN_USER_ROLE_UPDATE', '用户角色变更', 'ADMIN', '调整用户业务角色', 1),
    (92006, 'ADMIN_USER_PASSWORD_RESET', '用户密码重置', 'ADMIN', '重置用户登录密码', 1),
    (92007, 'ADMIN_ROLE_PERMISSION_ASSIGN', '角色权限分配', 'ADMIN', '维护角色权限矩阵', 1),
    (92008, 'ADMIN_CONFIG_READ', '系统配置读取', 'ADMIN', '读取系统配置项', 1),
    (92009, 'ADMIN_CONFIG_WRITE', '系统配置写入', 'ADMIN', '创建或更新系统配置项', 1),
    (92010, 'ADMIN_AUDIT_READ', '审计日志读取', 'ADMIN', '查询管理员审计日志', 1),
    (92011, 'EXAM_CREATE', '考试创建', 'EXAM', '创建考试并设置时间窗', 1),
    (92012, 'EXAM_SESSION_START', '考试会话开始', 'EXAM', '开始考试会话', 1),
    (92013, 'EXAM_ANSWER_SAVE', '考试答案保存', 'EXAM', '保存考试会话答案', 1),
    (92014, 'EXAM_SESSION_SUBMIT', '考试会话提交', 'EXAM', '提交考试会话', 1),
    (92015, 'GRADING_TASK_VIEW', '阅卷任务查看', 'GRADING', '查询阅卷任务列表', 1),
    (92016, 'GRADING_MANUAL_SCORE', '人工阅卷评分', 'GRADING', '提交主观题人工评分', 1),
    (92017, 'QUESTION_CREATE', '题目录入', 'QUESTION', '创建题目并落库', 1),
    (92018, 'QUESTION_LIST', '题目列表读取', 'QUESTION', '查询题目列表', 1),
    (92019, 'QUESTION_DETAIL', '题目详情读取', 'QUESTION', '查询题目详情', 1),
    (92020, 'PAPER_CREATE', '试卷创建', 'QUESTION', '创建试卷并绑定题目', 1),
    (92021, 'PAPER_DETAIL', '试卷详情读取', 'QUESTION', '查询试卷详情', 1),
    (92022, 'REPORT_SCORE_DISTRIBUTION_VIEW', '分数分布报表读取', 'ANALYSIS', '查询考试分数分布报表', 1),
    (92023, 'REPORT_QUESTION_ACCURACY_VIEW', '题目正确率报表读取', 'ANALYSIS', '查询题目正确率 TopN 报表', 1),
    (92024, 'USER_SELF_VIEW', '个人资料读取', 'USER', '查询当前登录用户资料', 1),
    (92025, 'USER_PROFILE_VIEW', '用户详情读取', 'USER', '按用户ID查询资料', 1),
    (92026, 'USER_LIST_VIEW', '用户列表读取', 'USER', '查询用户列表', 1),
    (92027, 'EXAM_ANTI_CHEAT_EVENT_REPORT', '防作弊事件上报', 'EXAM', '上报考试过程防作弊风险事件', 1),
    (92028, 'EXAM_ANTI_CHEAT_RISK_VIEW', '防作弊风险查看', 'EXAM', '查看考试会话风险评分与事件明细', 1)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    module_key = VALUES(module_key),
    description = VALUES(description),
    status = VALUES(status);

INSERT IGNORE INTO sys_role_permission (role_code, permission_code)
VALUES
    ('ADMIN', 'ADMIN_PLATFORM_ACCESS'),
    ('ADMIN', 'ADMIN_OVERVIEW_READ'),
    ('ADMIN', 'ADMIN_USER_VIEW'),
    ('ADMIN', 'ADMIN_USER_STATUS_UPDATE'),
    ('ADMIN', 'ADMIN_USER_ROLE_UPDATE'),
    ('ADMIN', 'ADMIN_USER_PASSWORD_RESET'),
    ('ADMIN', 'ADMIN_ROLE_PERMISSION_ASSIGN'),
    ('ADMIN', 'ADMIN_CONFIG_READ'),
    ('ADMIN', 'ADMIN_CONFIG_WRITE'),
    ('ADMIN', 'ADMIN_AUDIT_READ'),
    ('ADMIN', 'EXAM_CREATE'),
    ('ADMIN', 'EXAM_SESSION_START'),
    ('ADMIN', 'EXAM_ANSWER_SAVE'),
    ('ADMIN', 'EXAM_SESSION_SUBMIT'),
    ('ADMIN', 'EXAM_ANTI_CHEAT_EVENT_REPORT'),
    ('ADMIN', 'EXAM_ANTI_CHEAT_RISK_VIEW'),
    ('ADMIN', 'GRADING_TASK_VIEW'),
    ('ADMIN', 'GRADING_MANUAL_SCORE'),
    ('ADMIN', 'QUESTION_CREATE'),
    ('ADMIN', 'QUESTION_LIST'),
    ('ADMIN', 'QUESTION_DETAIL'),
    ('ADMIN', 'PAPER_CREATE'),
    ('ADMIN', 'PAPER_DETAIL'),
    ('ADMIN', 'REPORT_SCORE_DISTRIBUTION_VIEW'),
    ('ADMIN', 'REPORT_QUESTION_ACCURACY_VIEW'),
    ('ADMIN', 'USER_SELF_VIEW'),
    ('ADMIN', 'USER_PROFILE_VIEW'),
    ('ADMIN', 'USER_LIST_VIEW'),
    ('TEACHER', 'EXAM_CREATE'),
    ('TEACHER', 'EXAM_ANTI_CHEAT_RISK_VIEW'),
    ('TEACHER', 'GRADING_TASK_VIEW'),
    ('TEACHER', 'GRADING_MANUAL_SCORE'),
    ('TEACHER', 'QUESTION_CREATE'),
    ('TEACHER', 'QUESTION_LIST'),
    ('TEACHER', 'QUESTION_DETAIL'),
    ('TEACHER', 'PAPER_CREATE'),
    ('TEACHER', 'PAPER_DETAIL'),
    ('TEACHER', 'REPORT_SCORE_DISTRIBUTION_VIEW'),
    ('TEACHER', 'REPORT_QUESTION_ACCURACY_VIEW'),
    ('TEACHER', 'USER_SELF_VIEW'),
    ('TEACHER', 'USER_PROFILE_VIEW'),
    ('TEACHER', 'USER_LIST_VIEW'),
    ('STUDENT', 'EXAM_SESSION_START'),
    ('STUDENT', 'EXAM_ANSWER_SAVE'),
    ('STUDENT', 'EXAM_SESSION_SUBMIT'),
    ('STUDENT', 'EXAM_ANTI_CHEAT_EVENT_REPORT'),
    ('STUDENT', 'USER_SELF_VIEW');

INSERT INTO sys_config (config_key, config_value, group_key, description, updated_by)
VALUES
    ('ADMIN_PASSWORD_RESET_MIN_LENGTH', '8', 'SECURITY', '管理员重置密码最小长度策略', 10001),
    ('ADMIN_AUDIT_RETENTION_DAYS', '180', 'SECURITY', '审计日志保留天数（建议按归档策略执行）', 10001),
    ('ADMIN_OVERVIEW_CACHE_SECONDS', '30', 'PERFORMANCE', '管理员概览缓存时间（秒）', 10001)
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    group_key = VALUES(group_key),
    description = VALUES(description),
    updated_by = VALUES(updated_by);

USE question_db;
CREATE TABLE IF NOT EXISTS q_question (
    id BIGINT PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    stem TEXT NOT NULL,
    difficulty TINYINT NOT NULL,
    knowledge_point VARCHAR(128),
    analysis TEXT,
    answer TEXT NOT NULL,
    options_json JSON NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_q_question_created_at(created_at),
    INDEX idx_q_question_created_by_created_at(created_by, created_at)
);

CREATE TABLE IF NOT EXISTS q_paper (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    total_score INT NOT NULL,
    time_limit_minutes INT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_q_paper_created_at(created_at),
    INDEX idx_q_paper_created_by_created_at(created_by, created_at)
);

CREATE TABLE IF NOT EXISTS q_paper_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    score INT NOT NULL,
    order_no INT NOT NULL,
    UNIQUE KEY uk_q_paper_question_paper_order(paper_id, order_no),
    INDEX idx_q_paper_question_paper_id(paper_id),
    INDEX idx_q_paper_question_question_id(question_id)
);

USE exam_db;
CREATE TABLE IF NOT EXISTS e_exam (
    id BIGINT PRIMARY KEY,
    paper_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    anti_cheat_level TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL,
    INDEX idx_e_exam_time_window(start_time, end_time)
);

CREATE TABLE IF NOT EXISTS e_exam_target (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_e_exam_target_exam_student(exam_id, student_id),
    INDEX idx_e_exam_target_student(student_id, exam_id),
    INDEX idx_e_exam_target_exam(exam_id)
);

CREATE TABLE IF NOT EXISTS e_exam_session (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    submit_time DATETIME NULL,
    status VARCHAR(32) NOT NULL,
    ip_at_start VARCHAR(64),
    switch_screen_count INT NOT NULL DEFAULT 0,
    last_save_time DATETIME NULL,
    UNIQUE KEY uk_e_exam_session_exam_user(exam_id, user_id),
    INDEX idx_e_exam_session_exam_user(exam_id, user_id),
    INDEX idx_e_exam_session_status(status)
);

CREATE TABLE IF NOT EXISTS e_session_risk_event (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    exam_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_time DATETIME NOT NULL,
    event_score INT NOT NULL,
    payload_json TEXT,
    client_ip VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_e_session_risk_event_session(session_id, event_time),
    INDEX idx_e_session_risk_event_exam(exam_id, event_time)
);

CREATE TABLE IF NOT EXISTS e_session_risk_summary (
    session_id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    event_count INT NOT NULL DEFAULT 0,
    last_event_type VARCHAR(64),
    last_event_time DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_e_session_risk_summary_exam(exam_id, risk_level, risk_score)
);

CREATE TABLE IF NOT EXISTS e_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_content TEXT NOT NULL,
    is_marked_for_review TINYINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_e_answer_session_question(session_id, question_id),
    INDEX idx_e_answer_session_id(session_id)
);

USE grading_db;
CREATE TABLE IF NOT EXISTS g_grading_task (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    objective_score DECIMAL(10,2) NOT NULL DEFAULT 0,
    subjective_score DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_score DECIMAL(10,2) NOT NULL DEFAULT 0,
    grader_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_g_grading_task_session_id(session_id),
    INDEX idx_g_grading_task_exam_id(exam_id),
    INDEX idx_g_grading_task_status(status)
);

CREATE TABLE IF NOT EXISTS g_question_score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    got_score DECIMAL(10,2) NOT NULL,
    comment TEXT,
    is_objective TINYINT NOT NULL DEFAULT 1,
    INDEX idx_g_question_score_task_id(task_id)
);

USE analysis_db;
CREATE TABLE IF NOT EXISTS a_score (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    total_score DECIMAL(10,2) NOT NULL,
    class_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_a_score_session_id(session_id),
    INDEX idx_a_score_exam_id(exam_id)
);

CREATE TABLE IF NOT EXISTS a_session_question_score (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    got_score DECIMAL(10,2) NOT NULL,
    is_objective TINYINT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_a_session_question(session_id, question_id),
    INDEX idx_a_session_question_exam_objective(exam_id, is_objective),
    INDEX idx_a_session_question_exam_question(exam_id, question_id)
);
