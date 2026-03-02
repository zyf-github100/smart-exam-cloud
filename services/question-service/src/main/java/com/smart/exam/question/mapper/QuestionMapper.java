package com.smart.exam.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.exam.question.entity.QuestionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<QuestionEntity> {
}
