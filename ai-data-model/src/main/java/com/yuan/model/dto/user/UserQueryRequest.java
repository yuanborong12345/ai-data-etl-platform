package com.yuan.model.dto.user;

import lombok.Data;

@Data
public class UserQueryRequest {

    private Long id;
    private String userAccount;
    private String userName;
    private String userRole;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
