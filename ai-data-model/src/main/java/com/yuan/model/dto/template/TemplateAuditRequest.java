package com.yuan.model.dto.template;

import lombok.Data;

@Data
public class TemplateAuditRequest {
    private Long id;
    private Integer auditStatus;
    private String auditMsg;
}
