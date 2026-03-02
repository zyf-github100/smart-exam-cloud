package com.smart.exam.user.model;

public class UserProfile {

    private String id;
    private String username;
    private String realName;
    private String role;
    private String status;

    public UserProfile() {
    }

    public UserProfile(String id, String username, String realName, String role, String status) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.role = role;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

