package com.yuan.model.dto.template;

import lombok.Data;

@Data
public class TemplateQueryRequest {
    private String templateName;
    private Integer auditStatus;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
