package com.yuan.processor.consumer;

import com.yuan.constant.MqConstant;
import com.yuan.model.dto.processor.FileProcessMessage;
import com.yuan.processor.service.ParseResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者。
 *
 * 当文件处理消息经过所有重试仍然失败后进入死信队列，此处记录并告警。
 * {@link ProcessFileConsumer} 的 catch 块已尝试更新 TaskInfo 状态为失败，
 * 此处作为兜底，确保即使主消费者状态更新失败也能被记录。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final ParseResultService parseResultService;

    @RabbitListener(queues = MqConstant.QUEUE_DEAD_LETTER)
    public void handleDeadLetter(FileProcessMessage message) {
        log.error("【死信队列】收到处理失败的消息: taskId={}, fileId={}, fileName={}, templateId={}",
                message.getTaskId(), message.getFileId(), message.getFileName(), message.getTemplateId());

        try {
            parseResultService.markFailed(message.getTaskId(), "消息进入死信队列，处理失败");
        } catch (Exception e) {
            log.error("【死信队列】兜底更新任务失败状态也失败了: taskId={}", message.getTaskId(), e);
        }
    }
}
