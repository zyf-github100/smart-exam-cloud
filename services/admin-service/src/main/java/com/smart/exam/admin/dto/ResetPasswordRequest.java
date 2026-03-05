package com.smart.exam.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

    @NotBlank(message = "newPassword不能为空")
    @Size(min = 8, max = 64, message = "newPassword长度必须在8到64位之间")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{8,64}$",
            message = "newPassword必须同时包含大写字母、小写字母、数字和特殊字符，且不能包含空白字符"
    )
    private String newPassword;

    @Size(max = 255)
    private String reason;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
