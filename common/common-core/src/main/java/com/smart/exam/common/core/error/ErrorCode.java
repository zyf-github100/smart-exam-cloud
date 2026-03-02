package com.smart.exam.common.core.error;

public enum ErrorCode {
    SUCCESS(0, "OK"),
    BAD_REQUEST(40001, "参数错误"),
    UNAUTHORIZED(40100, "未登录或Token无效"),
    FORBIDDEN(40300, "无权限访问"),
    CONFLICT(40900, "重复提交"),
    NOT_FOUND(40400, "资源不存在"),
    INTERNAL_ERROR(50000, "系统错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

