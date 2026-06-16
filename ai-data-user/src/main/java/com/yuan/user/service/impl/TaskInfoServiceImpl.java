package com.yuan.user.service.impl;


import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuan.common.ErrorCode;
import com.yuan.exception.BusinessException;
import com.yuan.model.entity.FileInfo;
import com.yuan.model.entity.TaskInfo;
import com.yuan.user.mapper.TaskInfoMapper;
import com.yuan.user.service.TaskInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

/**
* @author dev
* @description 针对表【task_info(任务信息表)】的数据库操作Service实现
* @createDate 2026-06-12 14:15:30
*/
@Slf4j
@Service
public class TaskInfoServiceImpl extends ServiceImpl<TaskInfoMapper, TaskInfo>
    implements TaskInfoService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskInfo createPendingTask(FileInfo fileInfo, String promptContent, Long templateId) {
        String taskId = UUID.randomUUID().toString();
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setTaskId(taskId);
        taskInfo.setFileId(fileInfo.getId());
        taskInfo.setUserId(fileInfo.getUserId());
        taskInfo.setTemplateId(templateId);
        taskInfo.setPromptContent(promptContent);
        taskInfo.setStatus(1);
        taskInfo.setMqSendStatus(0);
        taskInfo.setMqRetryCount(0);
        taskInfo.setIsDelete(0);
        taskInfo.setCreateTime(new Date());
        taskInfo.setUpdateTime(new Date());
        taskInfo.setEditTime(new Date());

        boolean saved = save(taskInfo);
        if (!saved) {
            log.error("任务保存失败，fileId={}, taskId={}", fileInfo.getId(), taskId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "保存处理任务失败");
        }
        return taskInfo;
    }

    @Override
    public void updateMqSendStatus(String taskId, int mqSendStatus, Integer mqRetryCount, Date mqLastRetryTime) {
        LambdaUpdateWrapper<TaskInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TaskInfo::getTaskId, taskId)
                .set(TaskInfo::getMqSendStatus, mqSendStatus)
                .set(TaskInfo::getUpdateTime, new Date());
        if (mqRetryCount != null) {
            wrapper.set(TaskInfo::getMqRetryCount, mqRetryCount);
        }
        if (mqLastRetryTime != null) {
            wrapper.set(TaskInfo::getMqLastRetryTime, mqLastRetryTime);
        }
        update(wrapper);
    }
}
