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
    (92010, 'ADMIN_AUDIT_READ', '审计日志读取', 'ADMIN', '查询管理员审计日志', 1)
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
    ('ADMIN', 'ADMIN_AUDIT_READ');

INSERT INTO sys_config (config_key, config_value, group_key, description, updated_by)
VALUES
    ('ADMIN_PASSWORD_RESET_MIN_LENGTH', '6', 'SECURITY', '管理员重置密码最小长度策略', 10001),
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
    INDEX idx_q_question_created_at(created_at)
);

CREATE TABLE IF NOT EXISTS q_paper (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    total_score INT NOT NULL,
    time_limit_minutes INT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_q_paper_created_at(created_at)
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
    INDEX idx_e_exam_session_exam_user(exam_id, user_id),
    INDEX idx_e_exam_session_status(status)
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
