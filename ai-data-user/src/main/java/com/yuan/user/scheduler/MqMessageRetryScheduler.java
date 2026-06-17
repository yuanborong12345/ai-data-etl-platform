package com.yuan.user.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuan.constant.MqConstant;
import com.yuan.model.dto.processor.FileProcessMessage;
import com.yuan.model.entity.FileInfo;
import com.yuan.model.entity.TaskInfo;
import com.yuan.user.service.FileService;
import com.yuan.user.service.RabbitMqService;
import com.yuan.user.service.TaskInfoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * MQ 消息重试定时任务（本地消息表模式）。
 * 扫描 mqSendStatus IN (0,2) 且未超最大重试次数的 TaskInfo，重建并重发消息。
 */
@Slf4j
@Component
public class MqMessageRetryScheduler {

    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    @Resource
    private TaskInfoService taskInfoService;

    @Resource
    private FileService fileService;

    @Resource
    private RabbitMqService rabbitMqService;

    @Scheduled(fixedDelay = 10_000)
    public void retryFailedMessages() {
        LambdaQueryWrapper<TaskInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(TaskInfo::getMqSendStatus, 0, 2)
                .lt(TaskInfo::getMqRetryCount, MqConstant.DEFAULT_MAX_RETRY_COUNT)
                .orderByAsc(TaskInfo::getCreateTime);

        List<TaskInfo> pendingTasks = taskInfoService.list(queryWrapper);
        if (pendingTasks.isEmpty()) {
            return;
        }

        log.info("发现 {} 条待重试的MQ消息", pendingTasks.size());
        for (TaskInfo task : pendingTasks) {
            try {
                FileInfo fileInfo = fileService.getFileInfo(task.getFileId());
                FileProcessMessage message = new FileProcessMessage(
                        task.getTaskId(),
                        fileInfo.getId(),
                        task.getTemplateId(),
                        fileInfo.getStoragePath(),
                        fileInfo.getStorageType(),
                        fileInfo.getFileName(),
                        fileInfo.getUserId(),
                        task.getPromptContent()
                );

                int newRetryCount = (task.getMqRetryCount() != null ? task.getMqRetryCount() : 0) + 1;
                taskInfoService.updateMqSendStatus(task.getTaskId(), 0, newRetryCount, new Date());

                rabbitMqService.sendFileProcessMessage(message);
                log.info("MQ消息重试发送成功，taskId={}，retryCount={}", task.getTaskId(), newRetryCount);
            } catch (Exception e) {
                int newRetryCount = (task.getMqRetryCount() != null ? task.getMqRetryCount() : 0) + 1;
                taskInfoService.updateMqSendStatus(task.getTaskId(), 2, newRetryCount, new Date());
                log.error("MQ消息重试失败，taskId={}，retryCount={}", task.getTaskId(), newRetryCount, e);
            }
        }
    }
}
