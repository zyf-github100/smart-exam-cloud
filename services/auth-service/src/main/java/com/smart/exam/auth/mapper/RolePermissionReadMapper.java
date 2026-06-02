package com.smart.exam.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RolePermissionReadMapper {

    @Select("""
            SELECT rp.permission_code
            FROM admin_db.sys_role_permission rp
            INNER JOIN admin_db.sys_permission p ON p.permission_code = rp.permission_code
            WHERE rp.role_code = #{roleCode}
              AND p.status = 1
            ORDER BY rp.permission_code
            """)
    List<String> selectPermissionCodesByRoleCode(@Param("roleCode") String roleCode);
}
