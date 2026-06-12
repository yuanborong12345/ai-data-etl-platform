package com.yuan.model.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息 DTO（跨服务传输）
 */
@Data
public class UserDTO implements Serializable {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private LocalDateTime createTime;
}
