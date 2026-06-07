package com.yuan.model.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 文件元数据表
 * @TableName file_info
 */
@Data
public class FileInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型（扩展名，如 xlsx / csv） */
    private String fileType;

    /** 存储路径（本地路径或 OSS key） */
    private String storagePath;

    /** 上传用户ID */
    private Long userId;

    /** 任务ID（UUID，全链路追踪标识） */
    private String taskId;

    /** 文件状态：0上传中 1待解析 2解析中 3AI分析中 4完成 5失败 */
    private Integer status;

    /** 用户输入的分析需求描述 */
    private String promptContent;

    /** 失败原因 */
    private String errorMsg;

    /** 编辑时间 */
    private Date editTime;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 是否删除 */
    private Integer isDelete;
}
