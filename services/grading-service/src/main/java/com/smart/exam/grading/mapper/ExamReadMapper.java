package com.smart.exam.grading.mapper;

import com.smart.exam.grading.model.scoring.AnswerSnapshot;
import com.smart.exam.grading.model.scoring.ExamSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExamReadMapper {

    @Select("""
            SELECT id, paper_id AS paperId
            FROM exam_db.e_exam
            WHERE id = #{examId}
            LIMIT 1
            """)
    ExamSnapshot selectExamById(@Param("examId") Long examId);

    @Select("""
            SELECT question_id AS questionId, answer_content AS answerContent
            FROM exam_db.e_answer
            WHERE session_id = #{sessionId}
            """)
    List<AnswerSnapshot> selectAnswersBySessionId(@Param("sessionId") Long sessionId);
}
