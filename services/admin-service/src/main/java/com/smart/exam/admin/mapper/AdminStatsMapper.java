package com.smart.exam.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminStatsMapper {

    @Select("""
            SELECT COUNT(1)
            FROM sys_user
            """)
    Long countUsers();

    @Select("""
            SELECT COUNT(1)
            FROM sys_user
            WHERE status = 1
            """)
    Long countEnabledUsers();

    @Select("""
            SELECT COUNT(1)
            FROM sys_user
            WHERE status = 0
            """)
    Long countDisabledUsers();

    @Select("""
            SELECT COUNT(1)
            FROM exam_db.e_exam
            """)
    Long countExams();

    @Select("""
            SELECT COUNT(1)
            FROM exam_db.e_exam
            WHERE status = 'RUNNING'
            """)
    Long countRunningExams();

    @Select("""
            SELECT COUNT(1)
            FROM grading_db.g_grading_task
            WHERE status = 'MANUAL_REQUIRED'
            """)
    Long countManualRequiredTasks();

    @Select("""
            SELECT COUNT(1)
            FROM analysis_db.a_score
            """)
    Long countPublishedScores();

    @Select("""
            SELECT COUNT(1)
            FROM admin_db.sys_audit_log
            WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            """)
    Long countOperationsInLast24Hours();
}
