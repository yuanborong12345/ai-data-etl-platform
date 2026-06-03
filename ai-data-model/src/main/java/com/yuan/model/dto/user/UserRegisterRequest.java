package com.yuan.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 32, message = "账号长度 4-32 位")
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6-32 位")
    private String userPassword;

    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;
}
