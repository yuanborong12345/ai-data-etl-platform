package com.yuan.model.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserAdminEditRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Size(max = 32, message = "昵称最长32位")
    private String userName;

    private String userAvatar;

    @Size(max = 256, message = "简介最长256位")
    private String userProfile;

    private String userRole;

    private Integer status;
}
