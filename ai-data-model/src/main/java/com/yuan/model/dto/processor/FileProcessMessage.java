package com.yuan.model.dto.processor;

import com.yuan.model.enums.FileStorageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * RabbitMQ 消息体：User -> Processor
 * 文件上传完成后，User 服务投递此消息通知 Processor 开始 ETL。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileProcessMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 全链路追踪任务 ID */
    private String taskId;

    /** 文件 ID，关联 file_info 表 */
    private Long fileId;

    /** 文件存储路径 */
    private String storagePath;

    /** 文件存储类型 */
    private FileStorageType storageType;

    /** 原始文件名 */
    private String fileName;

    /** 上传用户 ID */
    private Long userId;

    /** 用户输入的分析需求描述 */
    private String promptContent;
}
