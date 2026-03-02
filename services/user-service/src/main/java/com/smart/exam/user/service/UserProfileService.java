package com.smart.exam.user.service;

import com.smart.exam.user.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserProfileService {

    private final Map<String, UserProfile> userStore = new ConcurrentHashMap<>();

    public UserProfileService() {
        userStore.put("10001", new UserProfile("10001", "admin", "系统管理员", "ADMIN", "ENABLED"));
        userStore.put("20001", new UserProfile("20001", "teacher1", "张老师", "TEACHER", "ENABLED"));
        userStore.put("30001", new UserProfile("30001", "stu1", "李同学", "STUDENT", "ENABLED"));
    }

    public UserProfile findById(String id) {
        return userStore.get(id);
    }

    public Collection<UserProfile> listAll() {
        return userStore.values();
    }
}

