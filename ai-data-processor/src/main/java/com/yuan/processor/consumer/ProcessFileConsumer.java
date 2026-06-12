package com.yuan.processor.consumer;

import com.yuan.constant.MqConstant;
import com.yuan.model.dto.processor.FileProcessMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProcessFileConsumer {

    /**
     * 监听文件处理队列
     */
    @RabbitListener(queues = MqConstant.QUEUE_FILE_PROCESS)
    public void processFileMessage(FileProcessMessage message) {
        log.info("【消费者】收到文件处理任务: {}", message);
        try {
            // TODO 解析
            // 例如：解析 Excel，存入数据库等
        } catch (Exception e) {
            log.error("【消费者】处理消息异常", e);
        }
    }
}