package com.smart.exam.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.exam.exam.entity.AnswerEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AnswerMapper extends BaseMapper<AnswerEntity> {

    @Select("""
            SELECT *
            FROM e_answer
            WHERE session_id = #{sessionId}
            ORDER BY id ASC
            """)
    List<AnswerEntity> selectBySessionIdOrdered(@Param("sessionId") Long sessionId);
}
