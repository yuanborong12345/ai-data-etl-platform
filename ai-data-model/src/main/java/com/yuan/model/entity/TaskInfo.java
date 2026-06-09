package com.yuan.model.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 任务信息表
 * @TableName task_info
 */
@Data
public class TaskInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 任务ID（UUID） */
    private String taskId;

    /** 关联的文件ID */
    private Long fileId;

    /** 关联的模板ID */
    private Long templateId;

    /** 任务所属用户ID */
    private Long userId;

    /** 用户输入的分析需求描述 */
    private String promptContent;

    /** 任务状态：0上传中 1待解析 2解析中 3AI分析中 4完成 5失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 结构化处理结果路径 */
    private String resultPath;

    /** AI报告文件路径 */
    private String reportPath;

    /** 解析开始时间 */
    private Date parseStartTime;

    /** 解析结束时间 */
    private Date parseEndTime;

    /** AI分析开始时间 */
    private Date analysisStartTime;

    /** AI分析结束时间 */
    private Date analysisEndTime;

    /** 编辑时间 */
    private Date editTime;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 是否删除 */
    private Integer isDelete;
}
