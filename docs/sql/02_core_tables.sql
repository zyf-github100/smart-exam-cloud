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

USE question_db;
CREATE TABLE IF NOT EXISTS q_question (
    id BIGINT PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    stem TEXT NOT NULL,
    difficulty TINYINT NOT NULL,
    knowledge_point VARCHAR(128),
    analysis TEXT,
    answer TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS q_paper (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    total_score INT NOT NULL,
    time_limit_minutes INT NOT NULL,
    created_by BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS q_paper_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    score INT NOT NULL,
    order_no INT NOT NULL
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
    created_by BIGINT NOT NULL
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
    last_save_time DATETIME NULL
);

CREATE TABLE IF NOT EXISTS e_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer_content TEXT NOT NULL,
    is_marked_for_review TINYINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

USE grading_db;
CREATE TABLE IF NOT EXISTS g_grading_task (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    objective_score DECIMAL(10,2) NOT NULL DEFAULT 0,
    subjective_score DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_score DECIMAL(10,2) NOT NULL DEFAULT 0,
    grader_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS g_question_score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    got_score DECIMAL(10,2) NOT NULL,
    comment TEXT,
    is_objective TINYINT NOT NULL DEFAULT 1
);

USE analysis_db;
CREATE TABLE IF NOT EXISTS a_score (
    id BIGINT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    total_score DECIMAL(10,2) NOT NULL,
    class_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

