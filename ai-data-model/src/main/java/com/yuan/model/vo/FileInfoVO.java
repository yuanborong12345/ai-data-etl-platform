package com.yuan.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 文件信息视图（脱敏后返回前端）
 */
@Data
public class FileInfoVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String taskId;
    private Integer status;
    private String promptContent;
    private String errorMsg;
    private Date createTime;
    private Date updateTime;
}
