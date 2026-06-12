package com.yuan.user.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.yuan.model.entity.FileInfo;
import com.yuan.model.entity.TaskInfo;

import java.util.Date;

/**
* @author dev
* @description 针对表【task_info(任务信息表)】的数据库操作Service
* @createDate 2026-06-12 14:15:30
*/
public interface TaskInfoService extends IService<TaskInfo> {

    /**
     * 在事务中创建待发送的任务记录（本地消息表模式）。
     *
     * @param fileInfo      关联的文件元数据
     * @param promptContent 用户分析需求描述
     * @return 持久化后的 TaskInfo（含生成的 taskId）
     */
    TaskInfo createPendingTask(FileInfo fileInfo, String promptContent);

    /**
     * 更新任务的 MQ 发送状态。
     *
     * @param taskId         任务ID
     * @param mqSendStatus   新状态：0-待发送 1-成功 2-失败
     * @param mqRetryCount   重试次数（null 表示不更新）
     * @param mqLastRetryTime 最后重试时间（null 表示不更新）
     */
    void updateMqSendStatus(String taskId, int mqSendStatus, Integer mqRetryCount, Date mqLastRetryTime);
}
