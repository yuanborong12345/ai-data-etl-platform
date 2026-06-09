package com.yuan.model.dto.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TemplateSchemaDTO {
    @NotNull(message = "列索引不能为空")
    private Integer columnIndex;

    @NotBlank(message = "列名不能为空")
    private String headerName;

    @NotBlank(message = "字段Key不能为空")
    private String fieldKey;

    @NotBlank(message = "数据类型不能为空")
    private String dataType;

    @NotNull(message = "是否必填不能为空")
    private Boolean required;
}