package com.yuan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 解析数据表 — 通用 JSON 行存储
 * @TableName parse_data
 */
@TableName(value = "parse_data")
@Data
public class ParseData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 任务 ID (UUID) */
    private String taskId;

    /** 行号（0-based） */
    private Integer rowIndex;

    /** 结构化行数据 JSON: {fieldKey: value} */
    private String rowData;

    /** 是否有效：1有效 0无效 */
    private Integer isValid;

    /** 校验错误信息 */
    private String validationError;

    /** 创建时间 */
    private Date createTime;
}
