package com.yuan.intelligence.consumer;

import com.yuan.constant.MqConstant;
import com.yuan.model.dto.processor.DataAnalysisMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 数据分析消费者（骨架）。
 *
 * <p>Processor ETL 完成后投递 DataAnalysisMessage 到此队列，
 * 后续将接入大模型执行 AI 分析并生成结构化报告。</p>
 */
@Component
@Slf4j
public class DataAnalysisConsumer {

    @RabbitListener(queues = MqConstant.QUEUE_DATA_ANALYSIS)
    public void handleAnalysisTask(DataAnalysisMessage message) {
        log.info("【AI分析】收到数据分析任务: taskId={}, fileId={}, templateId={}, userId={}",
                message.getTaskId(), message.getFileId(), message.getTemplateId(), message.getUserId());
        log.info("【AI分析】解析结果: totalRows={}, validRows={}, errorRows={}",
                message.getTotalRows(), message.getValidRows(), message.getErrorRows());
        log.info("【AI分析】用户分析需求: {}", message.getPromptContent());

        try {
            // TODO: 调用大模型进行 AI 分析，生成结构化报告
            log.info("【AI分析】任务处理完成(骨架): taskId={}", message.getTaskId());
        } catch (Exception e) {
            log.error("【AI分析】处理异常: taskId={}", message.getTaskId(), e);
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
