package com.yuan.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class TemplateInfoVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String templateName;
    private String description;
    private String templateSchema;
    private Long creatorId;
    private Integer auditStatus;
    private String auditMsg;
    private Date createTime;
    private Date updateTime;
}
