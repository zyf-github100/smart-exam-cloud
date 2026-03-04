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
