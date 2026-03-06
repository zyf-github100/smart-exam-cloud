package com.smart.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.exam.exam.entity.ExamSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExamSessionMapper extends BaseMapper<ExamSessionEntity> {

    @Select("""
            SELECT es.id
            FROM exam_db.e_exam_session es
            INNER JOIN exam_db.e_exam ee ON ee.id = es.exam_id
            WHERE es.status = 'IN_PROGRESS'
              AND ee.end_time <= #{now}
            ORDER BY ee.end_time ASC, es.id ASC
            LIMIT #{limit}
            """)
    List<Long> selectExpiredInProgressSessionIds(@Param("now") LocalDateTime now, @Param("limit") Long limit);

    @Update("""
            UPDATE exam_db.e_exam_session
            SET status = #{nextStatus},
                submit_time = #{submitTime}
            WHERE id = #{sessionId}
              AND status = #{expectedStatus}
            """)
    int updateStatusIfMatched(@Param("sessionId") Long sessionId,
                              @Param("expectedStatus") String expectedStatus,
                              @Param("nextStatus") String nextStatus,
                              @Param("submitTime") LocalDateTime submitTime);
}
