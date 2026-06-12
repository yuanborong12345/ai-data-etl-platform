package com.yuan.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 任务信息表
 * @TableName task_info
 */
@TableName(value ="task_info")
@Data
public class TaskInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
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

    /** MQ发送状态：0-待发送 1-发送成功 2-发送失败*/
    private Integer mqSendStatus;

    /** MQ消息重试次数，限制最大重试次数，防止死循环*/
    private Integer mqRetryCount;

    /** MQ最后重试时间*/
    private Date mqLastRetryTime;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        TaskInfo other = (TaskInfo) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
                && (this.getTaskId() == null ? other.getTaskId() == null : this.getTaskId().equals(other.getTaskId()))
                && (this.getFileId() == null ? other.getFileId() == null : this.getFileId().equals(other.getFileId()))
                && (this.getTemplateId() == null ? other.getTemplateId() == null : this.getTemplateId().equals(other.getTemplateId()))
                && (this.getUserId() == null ? other.getUserId() == null : this.getUserId().equals(other.getUserId()))
                && (this.getPromptContent() == null ? other.getPromptContent() == null : this.getPromptContent().equals(other.getPromptContent()))
                && (this.getStatus() == null ? other.getStatus() == null : this.getStatus().equals(other.getStatus()))
                && (this.getErrorMsg() == null ? other.getErrorMsg() == null : this.getErrorMsg().equals(other.getErrorMsg()))
                && (this.getResultPath() == null ? other.getResultPath() == null : this.getResultPath().equals(other.getResultPath()))
                && (this.getReportPath() == null ? other.getReportPath() == null : this.getReportPath().equals(other.getReportPath()))
                && (this.getParseStartTime() == null ? other.getParseStartTime() == null : this.getParseStartTime().equals(other.getParseStartTime()))
                && (this.getParseEndTime() == null ? other.getParseEndTime() == null : this.getParseEndTime().equals(other.getParseEndTime()))
                && (this.getAnalysisStartTime() == null ? other.getAnalysisStartTime() == null : this.getAnalysisStartTime().equals(other.getAnalysisStartTime()))
                && (this.getAnalysisEndTime() == null ? other.getAnalysisEndTime() == null : this.getAnalysisEndTime().equals(other.getAnalysisEndTime()))
                && (this.getEditTime() == null ? other.getEditTime() == null : this.getEditTime().equals(other.getEditTime()))
                && (this.getCreateTime() == null ? other.getCreateTime() == null : this.getCreateTime().equals(other.getCreateTime()))
                && (this.getUpdateTime() == null ? other.getUpdateTime() == null : this.getUpdateTime().equals(other.getUpdateTime()))
                && (this.getMqSendStatus() == null ? other.getMqSendStatus() == null : this.getMqSendStatus().equals(other.getMqSendStatus()))
                && (this.getMqRetryCount() == null ? other.getMqRetryCount() == null : this.getMqRetryCount().equals(other.getMqRetryCount()))
                && (this.getMqLastRetryTime() == null ? other.getMqLastRetryTime() == null : this.getMqLastRetryTime().equals(other.getMqLastRetryTime()))
                && (this.getIsDelete() == null ? other.getIsDelete() == null : this.getIsDelete().equals(other.getIsDelete()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getTaskId() == null) ? 0 : getTaskId().hashCode());
        result = prime * result + ((getFileId() == null) ? 0 : getFileId().hashCode());
        result = prime * result + ((getTemplateId() == null) ? 0 : getTemplateId().hashCode());
        result = prime * result + ((getUserId() == null) ? 0 : getUserId().hashCode());
        result = prime * result + ((getPromptContent() == null) ? 0 : getPromptContent().hashCode());
        result = prime * result + ((getStatus() == null) ? 0 : getStatus().hashCode());
        result = prime * result + ((getErrorMsg() == null) ? 0 : getErrorMsg().hashCode());
        result = prime * result + ((getResultPath() == null) ? 0 : getResultPath().hashCode());
        result = prime * result + ((getReportPath() == null) ? 0 : getReportPath().hashCode());
        result = prime * result + ((getParseStartTime() == null) ? 0 : getParseStartTime().hashCode());
        result = prime * result + ((getParseEndTime() == null) ? 0 : getParseEndTime().hashCode());
        result = prime * result + ((getAnalysisStartTime() == null) ? 0 : getAnalysisStartTime().hashCode());
        result = prime * result + ((getAnalysisEndTime() == null) ? 0 : getAnalysisEndTime().hashCode());
        result = prime * result + ((getEditTime() == null) ? 0 : getEditTime().hashCode());
        result = prime * result + ((getCreateTime() == null) ? 0 : getCreateTime().hashCode());
        result = prime * result + ((getUpdateTime() == null) ? 0 : getUpdateTime().hashCode());
        result = prime * result + ((getMqSendStatus() == null) ? 0 : getMqSendStatus().hashCode());
        result = prime * result + ((getMqRetryCount() == null) ? 0 : getMqRetryCount().hashCode());
        result = prime * result + ((getMqLastRetryTime() == null) ? 0 : getMqLastRetryTime().hashCode());
        result = prime * result + ((getIsDelete() == null) ? 0 : getIsDelete().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", taskId=").append(taskId);
        sb.append(", fileId=").append(fileId);
        sb.append(", templateId=").append(templateId);
        sb.append(", userId=").append(userId);
        sb.append(", promptContent=").append(promptContent);
        sb.append(", status=").append(status);
        sb.append(", errorMsg=").append(errorMsg);
        sb.append(", resultPath=").append(resultPath);
        sb.append(", reportPath=").append(reportPath);
        sb.append(", parseStartTime=").append(parseStartTime);
        sb.append(", parseEndTime=").append(parseEndTime);
        sb.append(", analysisStartTime=").append(analysisStartTime);
        sb.append(", analysisEndTime=").append(analysisEndTime);
        sb.append(", editTime=").append(editTime);
        sb.append(", createTime=").append(createTime);
        sb.append(", updateTime=").append(updateTime);
        sb.append(", isDelete=").append(isDelete);
        sb.append(", mqSendStatus=").append(mqSendStatus);
        sb.append(", mqRetryCount=").append(mqRetryCount);
        sb.append(", mqLastRetryTime=").append(mqLastRetryTime);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}