package com.yuan.model.dto.template;

import com.baomidou.mybatisplus.annotation.IdType;

import com.baomidou.mybatisplus.annotation.TableId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 业务模板创建请求
 */
@Data
public class TemplateCreateRequest implements Serializable {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板描述及使用说明
     */
    private String description;

    @NotEmpty(message = "模板列结构不能为空")
    @Valid // 嵌套校验，会自动校验 List 内部每个对象的属性
    private List<TemplateSchemaDTO> templateSchema;

    @Serial
    private static final long serialVersionUID = 1L;
}