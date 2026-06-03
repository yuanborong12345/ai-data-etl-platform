package com.yuan.model.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserSessionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;            // 用户ID
    private String userAccount; // 用户账号
    private String userName;    // 用户昵称
    private String userRole;    // 角色：user / admin
    private Integer status;     // 状态：0正常, 1禁用
    private String token;       // 当前合法生效的 Token 签名串（用于防多端顶号）
    private Long loginTime;     // 登录时间戳
}
