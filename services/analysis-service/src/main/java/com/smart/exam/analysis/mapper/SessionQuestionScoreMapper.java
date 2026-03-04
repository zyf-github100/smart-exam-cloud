package com.smart.exam.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.exam.analysis.entity.SessionQuestionScoreEntity;
import com.smart.exam.analysis.model.QuestionAccuracyItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SessionQuestionScoreMapper extends BaseMapper<SessionQuestionScoreEntity> {

    @Select("""
            SELECT
                question_id AS questionId,
                ROUND(SUM(got_score) / NULLIF(SUM(max_score), 0) * 100, 2) AS accuracy
            FROM a_session_question_score
            WHERE exam_id = #{examId}
              AND is_objective = 1
            GROUP BY question_id
            ORDER BY accuracy DESC, question_id ASC
            LIMIT #{top}
            """)
    List<QuestionAccuracyItem> selectQuestionAccuracyTop(@Param("examId") Long examId, @Param("top") int top);
}

