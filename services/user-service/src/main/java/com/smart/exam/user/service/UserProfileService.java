package com.smart.exam.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smart.exam.user.entity.SysUserEntity;
import com.smart.exam.user.mapper.SysUserMapper;
import com.smart.exam.user.model.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class UserProfileService {

    private final SysUserMapper sysUserMapper;

    public UserProfileService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    public UserProfile findById(String id) {
        SysUserEntity entity = sysUserMapper.selectById(parseLong(id));
        if (entity == null) {
            return null;
        }
        return toProfile(entity);
    }

    public List<UserProfile> listAll() {
        List<SysUserEntity> entities = sysUserMapper.selectList(
                Wrappers.lambdaQuery(SysUserEntity.class)
                        .orderByAsc(SysUserEntity::getId)
        );
        return entities.stream().map(this::toProfile).toList();
    }

    public List<UserProfile> listVisibleForRole(String requesterRole) {
        String normalizedRole = normalizeRole(requesterRole);
        if ("ADMIN".equals(normalizedRole)) {
            return listAll();
        }
        if ("TEACHER".equals(normalizedRole)) {
            return listByRole("STUDENT");
        }
        return List.of();
    }

    private List<UserProfile> listByRole(String role) {
        String normalizedRole = normalizeRole(role);
        if (!StringUtils.hasText(normalizedRole)) {
            return List.of();
        }

        List<SysUserEntity> entities = sysUserMapper.selectList(
                Wrappers.lambdaQuery(SysUserEntity.class)
                        .eq(SysUserEntity::getRole, normalizedRole)
                        .orderByAsc(SysUserEntity::getId)
        );
        return entities.stream().map(this::toProfile).toList();
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private UserProfile toProfile(SysUserEntity entity) {
        UserProfile profile = new UserProfile();
        profile.setId(String.valueOf(entity.getId()));
        profile.setUsername(entity.getUsername());
        profile.setRealName(entity.getRealName());
        profile.setRole(entity.getRole());
        profile.setStatus(entity.getStatus() != null && entity.getStatus() == 1 ? "ENABLED" : "DISABLED");
        return profile;
    }

    private long parseLong(String rawValue) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

}
