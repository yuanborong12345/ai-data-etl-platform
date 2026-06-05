package com.yuan.model.dto.gateway;

import lombok.Data;

/**
 * 内部承载 Redis JSON 转换的纯净静态 DTO 类
 */
@Data
public class GatewaySessionDTO {
    private Long userId;
    private String token;
    private Integer status;
}