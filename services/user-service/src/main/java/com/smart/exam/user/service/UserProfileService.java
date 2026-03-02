package com.smart.exam.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.exam.user.entity.SysUserEntity;
import com.smart.exam.user.mapper.SysUserMapper;
import com.smart.exam.user.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);
    private static final Duration PROFILE_CACHE_TTL = Duration.ofMinutes(20);
    private static final Duration LIST_CACHE_TTL = Duration.ofMinutes(5);
    private static final String PROFILE_CACHE_PREFIX = "user:profile:";
    private static final String LIST_CACHE_KEY = "user:list";

    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserProfileService(SysUserMapper sysUserMapper,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper) {
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public UserProfile findById(String id) {
        String cacheKey = PROFILE_CACHE_PREFIX + id;
        UserProfile cached = getCache(cacheKey, UserProfile.class);
        if (cached != null) {
            return cached;
        }

        SysUserEntity entity = sysUserMapper.selectById(parseLong(id));
        if (entity == null) {
            return null;
        }

        UserProfile profile = toProfile(entity);
        putCache(cacheKey, profile, PROFILE_CACHE_TTL);
        return profile;
    }

    public List<UserProfile> listAll() {
        List<UserProfile> cached = getListCache();
        if (cached != null) {
            return cached;
        }

        List<SysUserEntity> entities = sysUserMapper.selectList(
                Wrappers.lambdaQuery(SysUserEntity.class)
                        .orderByAsc(SysUserEntity::getId)
        );
        List<UserProfile> profiles = entities.stream().map(this::toProfile).toList();
        putCache(LIST_CACHE_KEY, profiles, LIST_CACHE_TTL);
        return profiles;
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

    private List<UserProfile> getListCache() {
        try {
            String raw = redisTemplate.opsForValue().get(LIST_CACHE_KEY);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, new TypeReference<List<UserProfile>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to read list cache", ex);
            return null;
        }
    }

    private <T> T getCache(String key, Class<T> clazz) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, clazz);
        } catch (Exception ex) {
            log.warn("Failed to read cache, key={}", key, ex);
            return null;
        }
    }

    private void putCache(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize cache value, key={}", key, ex);
        } catch (Exception ex) {
            log.warn("Failed to write cache, key={}", key, ex);
        }
    }
}
