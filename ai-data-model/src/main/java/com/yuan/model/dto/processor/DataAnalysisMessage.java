package com.yuan.model.dto.processor;

import com.yuan.model.dto.template.TemplateSchemaDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * RabbitMQ 消息体：Processor -> Intelligence
 * Processor ETL 完成后投递此消息通知 Intelligence 开始 AI 分析。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataAnalysisMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 全链路追踪任务 ID */
    private String taskId;

    /** 文件 ID */
    private Long fileId;

    /** 模板 ID */
    private Long templateId;

    /** 模板列定义 */
    private List<TemplateSchemaDTO> templateSchema;

    /** 上传用户 ID */
    private Long userId;

    /** 用户输入的分析需求描述 */
    private String promptContent;

    /** 解析后结构化数据的引用 */
    private String parsedDataRef;

    /** 总行数 */
    private Integer totalRows;

    /** 有效行数 */
    private Integer validRows;

    /** 错误行数 */
    private Integer errorRows;

    /** 解析错误摘要（JSON） */
    private String parseErrors;
}
