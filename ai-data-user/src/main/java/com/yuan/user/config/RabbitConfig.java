package com.yuan.user.config;

import com.yuan.constant.MqConstant;
import com.yuan.user.service.TaskInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

/**
 * User 模块 RabbitMQ 配置（生产者）
 * 声明交换机并提供消息发送模板（JSON 序列化），不声明队列/binding（归属消费者 Processor）。
 */
@Slf4j
@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange dataExchange() {
        return ExchangeBuilder.directExchange(MqConstant.EXCHANGE_DATA)
                .durable(true)
                .build();
    }

    @Bean
    public RabbitTemplate amqpTemplate(ConnectionFactory connectionFactory,
                                       Jackson2JsonMessageConverter jackson2JsonMessageConverter,
                                       RabbitTemplate.ConfirmCallback confirmCallback,
                                       RabbitTemplate.ReturnsCallback returnsCallback) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        rabbitTemplate.setConfirmCallback(confirmCallback);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returnsCallback);
        return rabbitTemplate;
    }

    /**
     * 自定义 ConfirmCallback：根据 broker ack/nack 更新 TaskInfo.mqSendStatus。
     */
    @Bean
    public RabbitTemplate.ConfirmCallback confirmCallback(TaskInfoService taskInfoService) {
        return (CorrelationData correlationData, boolean ack, String cause) -> {
            String taskId = correlationData != null ? correlationData.getId() : null;
            if (taskId == null) {
                log.warn("taskId 为空，无法更新任务状态");
                return;
            }
            if (ack) {
                taskInfoService.updateMqSendStatus(taskId, 1, null, null);
                log.info("文件处理MQ消息投递成功，taskId={}", taskId);
            } else {
                taskInfoService.updateMqSendStatus(taskId, 2, null, new Date());
                log.error("文件处理MQ消息投递失败，taskId={}，cause={}", taskId, cause);
            }
        };
    }
}
