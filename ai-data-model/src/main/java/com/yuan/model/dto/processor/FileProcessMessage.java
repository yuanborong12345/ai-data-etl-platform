package com.yuan.model.dto.processor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * RabbitMQ 消息体：User → Processor
 * 文件上传完成后，User 服务投递此消息通知 Processor 开始 ETL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileProcessMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 全链路追踪任务ID */
    private String taskId;

    /** 文件ID（关联 file_info 表） */
    private Long fileId;

    /** 文件存储路径 */
    private String storagePath;

    /** 原始文件名 */
    private String fileName;

    /** 上传用户ID */
    private Long userId;

    /** 用户输入的分析需求描述 */
    private String promptContent;
}
