package com.smart.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.exam.exam.entity.SessionRiskSummaryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;

@Mapper
public interface SessionRiskSummaryMapper extends BaseMapper<SessionRiskSummaryEntity> {

    @Select("""
            SELECT *
            FROM exam_db.e_session_risk_summary
            WHERE session_id = #{sessionId}
            FOR UPDATE
            """)
    SessionRiskSummaryEntity selectByIdForUpdate(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO exam_db.e_session_risk_summary (
                session_id,
                exam_id,
                user_id,
                risk_score,
                risk_level,
                event_count,
                last_event_type,
                last_event_time,
                updated_at
            ) VALUES (
                #{sessionId},
                #{examId},
                #{userId},
                #{eventScore},
                #{initialRiskLevel},
                1,
                #{eventType},
                #{eventTime},
                #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE
                risk_level = CASE
                    WHEN risk_score + VALUES(risk_score) >= #{criticalThreshold} THEN 'CRITICAL'
                    WHEN risk_score + VALUES(risk_score) >= #{highThreshold} THEN 'HIGH'
                    WHEN risk_score + VALUES(risk_score) >= #{mediumThreshold} THEN 'MEDIUM'
                    ELSE 'LOW'
                END,
                risk_score = risk_score + VALUES(risk_score),
                event_count = event_count + 1,
                last_event_type = VALUES(last_event_type),
                last_event_time = VALUES(last_event_time),
                updated_at = VALUES(updated_at)
            """)
    int upsertIncrement(@Param("sessionId") Long sessionId,
                        @Param("examId") Long examId,
                        @Param("userId") Long userId,
                        @Param("eventScore") Integer eventScore,
                        @Param("initialRiskLevel") String initialRiskLevel,
                        @Param("eventType") String eventType,
                        @Param("eventTime") LocalDateTime eventTime,
                        @Param("updatedAt") LocalDateTime updatedAt,
                        @Param("mediumThreshold") Integer mediumThreshold,
                        @Param("highThreshold") Integer highThreshold,
                        @Param("criticalThreshold") Integer criticalThreshold);
}
