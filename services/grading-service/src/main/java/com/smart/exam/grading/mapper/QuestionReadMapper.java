package com.smart.exam.grading.mapper;

import com.smart.exam.grading.model.scoring.PaperQuestionSnapshot;
import com.smart.exam.grading.model.scoring.PaperQuestionDetailSnapshot;
import com.smart.exam.grading.model.scoring.QuestionSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface QuestionReadMapper {

    @Select("""
            SELECT question_id AS questionId, score, order_no AS orderNo
            FROM question_db.q_paper_question
            WHERE paper_id = #{paperId}
            ORDER BY order_no ASC, id ASC
            """)
    List<PaperQuestionSnapshot> selectPaperQuestionsByPaperId(@Param("paperId") Long paperId);

    @Select("""
            SELECT
                pq.question_id AS questionId,
                pq.score,
                pq.order_no AS orderNo,
                q.type,
                q.stem,
                q.analysis,
                q.answer,
                q.options_json AS optionsJson
            FROM question_db.q_paper_question pq
            INNER JOIN question_db.q_question q ON q.id = pq.question_id
            WHERE pq.paper_id = #{paperId}
            ORDER BY pq.order_no ASC, pq.id ASC
            """)
    List<PaperQuestionDetailSnapshot> selectPaperQuestionDetailsByPaperId(@Param("paperId") Long paperId);

    @Select("""
            <script>
            SELECT id, type, answer
            FROM question_db.q_question
            WHERE id IN
            <foreach item="questionId" collection="questionIds" open="(" separator="," close=")">
              #{questionId}
            </foreach>
            </script>
            """)
    List<QuestionSnapshot> selectQuestionsByIds(@Param("questionIds") Collection<Long> questionIds);
}
