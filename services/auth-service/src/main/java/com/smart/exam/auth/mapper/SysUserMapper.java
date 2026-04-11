package com.smart.exam.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.exam.auth.entity.SysUserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {

    @Select("""
            SELECT COUNT(1)
            FROM sys_user
            WHERE password_hash IS NOT NULL
              AND password_hash NOT LIKE '$2%'
            """)
    long countLegacyPasswordUsers();

    @Select("""
            SELECT username
            FROM sys_user
            WHERE password_hash IS NOT NULL
              AND password_hash NOT LIKE '$2%'
            ORDER BY id
            LIMIT #{limit}
            """)
    List<String> selectLegacyPasswordUsernames(@Param("limit") int limit);
}
