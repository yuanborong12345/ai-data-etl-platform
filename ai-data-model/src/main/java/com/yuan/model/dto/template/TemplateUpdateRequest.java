package com.yuan.model.dto.template;

import lombok.Data;

@Data
public class TemplateUpdateRequest {
    private Long id;
    private String templateName;
    private String description;
    private String templateSchema;
}
