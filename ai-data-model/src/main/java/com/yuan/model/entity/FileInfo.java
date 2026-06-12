package com.yuan.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.yuan.model.enums.FileStorageType;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 文件元数据表
 * @TableName file_info
 */
@TableName(value ="file_info")
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

    /** MIME类型 */
    private String contentType;

    /** 存储路径（本地路径或 OSS key） */
    private String storagePath;

    /** 存储类型：local/oss/minio */
    private FileStorageType storageType;

    /** 文件MD5 */
    private String fileMd5;

    /** 上传用户ID */
    private Long userId;

    /** 编辑时间 */
    private Date editTime;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 是否删除 */
    private Integer isDelete;
}
